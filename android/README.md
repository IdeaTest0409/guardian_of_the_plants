# smartphonapptest001

Android 向けのローカル / クラウド切替チャットアプリの Step 1 実装です。

## できること

- Jetpack Compose ベースのチャット UI
- LM Studio 互換 API への接続
- local / cloud の切替
- base URL / model / API key の保存
- ストリーミング応答の土台
- 将来の画像 / 音声入力を見据えた `Attachment` 抽象

## 初期設定

- Local base URL の既定値は `http://192.168.0.11:1234/v1` です。
- 実機から使う場合は、この LAN アドレスをそのまま利用できます。

## モデル

- Local: `gemma-4-e2b`
- Cloud: `gemma-4-e4b`

Settings 画面で変更できます。

## 補足

- この作業環境には `java` / `gradle` が入っていないため、実ビルド確認はできていません。
- ただし、Android Studio でそのまま開けるように Gradle 構成一式は配置済みです。
