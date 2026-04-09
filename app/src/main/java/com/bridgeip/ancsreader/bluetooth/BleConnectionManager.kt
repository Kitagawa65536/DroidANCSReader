package com.bridgeip.ancsreader.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.bridgeip.ancsreader.data.model.ConnectionStage
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleConnectionManager(
    private val context: Context,
    private val ancsManager: AncsManager,
    private val logger: (String) -> Unit,
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceSummary>>(emptyList())
    val services: StateFlow<List<GattServiceSummary>> = _services.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var pendingConnectAfterBond: BluetoothDevice? = null
    private var notificationSourceCharacteristic: BluetoothGattCharacteristic? = null
    private var dataSourceCharacteristic: BluetoothGattCharacteristic? = null
    private var controlPointCharacteristic: BluetoothGattCharacteristic? = null

    private val operationQueue = ArrayDeque<GattOperation>()
    private var currentOperation: GattOperation? = null

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = intent?.bluetoothDeviceExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (device.address != pendingConnectAfterBond?.address) {
                return
            }

            when (intent?.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> {
                    logger("Bonding completed for ${device.address}")
                    pendingConnectAfterBond = null
                    openGatt(device)
                }

                BluetoothDevice.BOND_NONE -> {
                    logger("Bonding was cancelled or failed for ${device.address}")
                    pendingConnectAfterBond = null
                    updateStatus(
                        stage = ConnectionStage.Error,
                        message = "Bonding failed. Confirm pairing on iPhone and retry.",
                    )
                }
            }
        }
    }

    init {
        context.registerReceiver(
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
        )
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            updateStatus(ConnectionStage.Error, address, "Bluetooth is disabled.")
            return
        }

        val device = adapter.getRemoteDevice(address)
        closeGatt()

        if (device.bondState == BluetoothDevice.BOND_NONE) {
            pendingConnectAfterBond = device
            updateStatus(ConnectionStage.Bonding, address, "Waiting for Bluetooth pairing approval on iPhone…")
            val started = try {
                device.createBond()
            } catch (_: SecurityException) {
                false
            }
            if (!started) {
                logger("createBond() was rejected; attempting direct GATT connection")
                pendingConnectAfterBond = null
                openGatt(device)
            }
        } else {
            openGatt(device)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        logger("Disconnect requested")
        bluetoothGatt?.disconnect()
        closeGatt()
        ancsManager.resetSession()
        _services.value = emptyList()
        updateStatus(ConnectionStage.Disconnected, _connectedDeviceAddress.value, "Disconnected")
        _connectedDeviceAddress.value = null
    }

    fun requestNotificationAction(notificationUid: Long, action: NotificationAction) {
        ancsManager.performAction(notificationUid, action)
    }

    private fun updateStatus(
        stage: ConnectionStage,
        address: String? = _connectedDeviceAddress.value,
        message: String,
    ) {
        _connectionStatus.value = ConnectionStatus(
            stage = stage,
            deviceAddress = address,
            message = message,
        )
    }

    @SuppressLint("MissingPermission")
    private fun openGatt(device: BluetoothDevice) {
        updateStatus(ConnectionStage.Connecting, device.address, "Connecting to ${device.name ?: device.address}…")
        logger("Opening GATT for ${device.address}")
        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        currentOperation = null
        operationQueue.clear()
        notificationSourceCharacteristic = null
        dataSourceCharacteristic = null
        controlPointCharacteristic = null
    }

    private fun queueOperation(operation: GattOperation) {
        operationQueue.addLast(operation)
        drainOperationQueue()
    }

    @SuppressLint("MissingPermission")
    private fun drainOperationQueue() {
        if (currentOperation != null) {
            return
        }

        val gatt = bluetoothGatt ?: return
        val operation = operationQueue.removeFirstOrNull() ?: run {
            if (notificationSourceCharacteristic != null &&
                dataSourceCharacteristic != null &&
                controlPointCharacteristic != null &&
                _connectionStatus.value.stage != ConnectionStage.Ready
            ) {
                ancsManager.attachControlPointWriter(::writeControlPoint)
                updateStatus(
                    stage = ConnectionStage.Ready,
                    message = "ANCS is ready. Incoming iPhone notifications will appear below.",
                )
            }
            return
        }

        val started = when (operation) {
            GattOperation.RequestMtu -> gatt.requestMtu(247)
            GattOperation.DiscoverServices -> gatt.discoverServices()
            is GattOperation.EnableNotifications -> enableNotifications(gatt, operation.characteristic)
        }

        if (started) {
            currentOperation = operation
        } else {
            logger("Failed to start GATT operation: $operation")
            currentOperation = null
            drainOperationQueue()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val descriptor = characteristic.getDescriptor(AncsBluetoothConstants.clientCharacteristicConfigUuid)
            ?: return false
        val localNotificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
        if (!localNotificationEnabled) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeControlPoint(payload: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val characteristic = controlPointCharacteristic ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun completeOperation() {
        currentOperation = null
        drainOperationQueue()
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt) {
        val summaries = gatt.services.map { service ->
            GattServiceSummary(
                uuid = service.uuid.toString(),
                characteristics = service.characteristics.map { it.uuid.toString() },
            )
        }
        _services.value = summaries
        summaries.forEach { summary ->
            logger("Service ${summary.uuid} -> ${summary.characteristics.joinToString()}")
        }

        val ancsService = gatt.getService(AncsBluetoothConstants.ancsServiceUuid)
        if (ancsService == null) {
            updateStatus(
                stage = ConnectionStage.Error,
                message = "Connected, but ANCS service was not found. Unlock iPhone and confirm notification sharing.",
            )
            logger("ANCS service not found on the connected device")
            return
        }

        notificationSourceCharacteristic = ancsService.getCharacteristic(AncsBluetoothConstants.notificationSourceUuid)
        dataSourceCharacteristic = ancsService.getCharacteristic(AncsBluetoothConstants.dataSourceUuid)
        controlPointCharacteristic = ancsService.getCharacteristic(AncsBluetoothConstants.controlPointUuid)

        if (notificationSourceCharacteristic == null || dataSourceCharacteristic == null || controlPointCharacteristic == null) {
            updateStatus(ConnectionStage.Error, message = "ANCS service is incomplete on this connection.")
            logger("ANCS service exists but mandatory characteristics were missing")
            return
        }

        updateStatus(ConnectionStage.Subscribing, message = "Subscribing to Notification Source and Data Source…")
        queueOperation(GattOperation.EnableNotifications(notificationSourceCharacteristic!!))
        queueOperation(GattOperation.EnableNotifications(dataSourceCharacteristic!!))
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                _connectedDeviceAddress.value = gatt.device.address
                updateStatus(ConnectionStage.DiscoveringServices, gatt.device.address, "Connected. Negotiating MTU…")
                logger("GATT connected: status=$status device=${gatt.device.address}")
                queueOperation(GattOperation.RequestMtu)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                logger("GATT disconnected: status=$status device=${gatt.device.address}")
                ancsManager.resetSession()
                closeGatt()
                _services.value = emptyList()
                _connectedDeviceAddress.value = null
                updateStatus(ConnectionStage.Disconnected, gatt.device.address, "Disconnected from iPhone")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            logger("MTU updated to $mtu (status=$status)")
            completeOperation()
            queueOperation(GattOperation.DiscoverServices)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            logger("Service discovery finished with status=$status")
            completeOperation()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleServicesDiscovered(gatt)
            } else {
                updateStatus(ConnectionStage.Error, message = "Service discovery failed: $status")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            logger("Descriptor write ${descriptor.characteristic.uuid} status=$status")
            completeOperation()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            logger("Characteristic write ${characteristic.uuid} status=$status")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChange(characteristic.uuid.toString(), characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChange(characteristic.uuid.toString(), value)
        }
    }

    private fun handleCharacteristicChange(
        uuid: String,
        value: ByteArray,
    ) {
        when (uuid) {
            AncsBluetoothConstants.notificationSourceUuid.toString() -> ancsManager.onNotificationSourceChanged(value)
            AncsBluetoothConstants.dataSourceUuid.toString() -> ancsManager.onDataSourceChanged(value)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDeviceExtra(name: String): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(name)
        }
    }

    private sealed interface GattOperation {
        data object RequestMtu : GattOperation
        data object DiscoverServices : GattOperation
        data class EnableNotifications(val characteristic: BluetoothGattCharacteristic) : GattOperation
    }
}
