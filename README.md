# ANCS Reader

Kotlin + Jetpack Compose + Material 3 で作った Android 向けの ANCS (Apple Notification Center Service) クライアントです。BLE で iPhone に接続し、ANCS の `Notification Source` / `Data Source` / `Control Point` を使って iPhone 通知を Android 側に一覧表示します。

## 主要構成

```text
ANCSReader/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ java/com/bridgeip/ancsreader/
│     │  │  ├─ AncsReaderApplication.kt
│     │  │  ├─ AppContainer.kt
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ bluetooth/
│     │  │  ├─ data/model/
│     │  │  ├─ data/parser/
│     │  │  ├─ data/repository/
│     │  │  ├─ service/
│     │  │  ├─ ui/
│     │  │  ├─ util/
│     │  │  └─ viewmodel/
│     │  └─ res/
│     └─ test/
│        └─ java/com/bridgeip/ancsreader/data/parser/
├─ build.gradle.kts
├─ gradle/libs.versions.toml
└─ settings.gradle.kts
```

## アーキテクチャ

- `bluetooth/AndroidBleScanner`
  BLE スキャンを担当します。デバイス名固定には依存せず、近傍の BLE デバイスを一覧化します。
- `bluetooth/BleConnectionManager`
  ペアリング、GATT 接続、MTU 交渉、Service 検出、ANCS Characteristic subscribe を担当します。
- `bluetooth/AncsManager`
  `Notification Source` のイベント処理、`Control Point` の属性取得リクエスト、`Data Source` の再構成を担当します。
- `data/repository/DefaultAncsRepository`
  BLE/ANCS イベントを `StateFlow` に集約し、UI が扱いやすい通知一覧に整形します。
- `viewmodel/AncsViewModel`
  権限状態と Repository の状態を結合して immutable UI state を生成します。
- `ui/`
  Compose 画面。接続、通知一覧、デバッグの 3 タブ構成です。
- `service/ConnectionForegroundService`
  保存済みの iPhone へ再接続し、ANCS をバックグラウンドで維持しながら Android 通知へミラーします。

## 実装済み機能

- iPhone と BLE 接続開始
- ANCS Service UUID 検出
- `Notification Source` subscribe
- `Data Source` subscribe
- `Control Point` への `Get Notification Attributes` 送信
- タイトル / サブタイトル / 本文 / 日時 / Action label 取得
- 複数パケットの `Data Source` 応答再構成
- 通知 UID ベースの追加 / 更新 / 削除反映
- Positive / Negative Action コマンド送信
- Foreground Service からの常駐接続と Android 通知ミラー表示
- 受信済み通知ログの永続保存、個別削除、全削除
- 最後に接続した iPhone の保存とバックグラウンド再接続
- Android 12+ の `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` 対応
- 旧 Android 向けの `BLUETOOTH` / `BLUETOOTH_ADMIN` / `ACCESS_FINE_LOCATION` 条件対応
- GATT サービス一覧と ANCS 通信ログの表示

## セットアップ

### Android 側

1. Android Studio でこのプロジェクトを開きます。
2. 初回ビルド時に必要な SDK / Gradle を同期します。
3. 実機の Android 12+ では Bluetooth 権限を許可します。
4. Bluetooth を有効化します。

### iPhone 側

1. iPhone の Bluetooth を有効にします。
2. 通知を出したいアプリの通知を有効にしておきます。
3. Android アプリからスキャン後、対象 iPhone へ接続を開始します。
4. iPhone 側に Bluetooth ペアリング確認や通知共有の許可ダイアログが出たら承認します。
5. iPhone はロック解除した状態で試すと ANCS Service が公開されやすくなります。

## 使い方

1. `Connect` タブで Bluetooth 権限を許可します。
2. Bluetooth をオンにします。
3. `Start scan` を押し、候補デバイスから iPhone を選んで `Pair and connect` を押します。
4. 一度接続すると、その iPhone は背景サービス用の再接続先として保存されます。
5. `Background Service` セクションから常駐サービスを起動すると、アプリを閉じても通知受信を継続できます。
6. 接続が進むと `Debug` タブに GATT Service / Characteristic ログが出ます。
7. `Ready` になった後、iPhone に新しい通知を発生させると Android 通知として表示され、`Notifications` タブにも保存されます。
8. `Notifications` タブでは保存済みログの個別削除と全削除ができます。
9. 対応通知では Positive / Negative Action ボタンを押せます。

## ビルド方法

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-home"
.\gradlew.bat :app:assembleDebug
```

テスト:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-home"
.\gradlew.bat :app:testDebugUnitTest
```

## ANCS 実装メモ

- ANCS UUID は Apple の仕様に基づいて定数管理しています。
- `Notification Source` 受信後、UID をキーに一覧状態を更新し、必要属性を `Control Point` へ要求します。
- `Data Source` は MTU 超過で分割されるため、現在の要求に対してフラグメントを結合してからパースしています。
- セッションが切れたら UID キャッシュを破棄します。

参考にした考え方:

- Apple ANCS Specification
  https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html
- brookwillow/ANCSReader
  https://github.com/brookwillow/ANCSReader

## 既知の制約

- iPhone の広告内容だけで ANCS 対応端末を判別していないため、BLE デバイス一覧から手動選択が必要です。
- `Service Changed` characteristic の監視や自動再接続は MVP では未実装です。
- BLE 接続とペアリングの挙動は Android 端末ベンダー差があります。
- `NotificationAttributeIDMessageSize` や `Get App Attributes` は未実装です。
- 端末や OS 状況によっては、長時間バックグラウンド維持に追加のバッテリー最適化除外が必要な場合があります。

## 今後の改善候補

- `Service Changed` subscribe と ANCS 再公開検知
- 自動再接続ポリシーと接続リトライの改善
- 通知詳細画面とアプリ名キャッシュ
- ログ永続化とエクスポート
- BLE 操作の queue をさらに厳密化してタイムアウト処理を追加
