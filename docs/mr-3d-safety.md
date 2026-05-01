# MR / 3D Safety Rules

Use these rules when editing the Android MR and 3D avatar code.

## Current Safe Direction

```text
Realtime camera MR -> 3D avatar allowed
Still photo background -> 2D avatar fallback
Image file upload -> 2D avatar fallback
3D expressions -> off by default
```

## Avoid

- Do not reintroduce still-image background plus SceneView 3D overlay.
- Do not use SceneView `ImageNode` for still photo backgrounds.
- Do not enable morph target expression driving by default.
- Do not assume Filament crashes are deterministic across devices.

## Reason

Past experiments with static image backgrounds and SceneView/Filament caused
black backgrounds or native crashes. Realtime camera MR has worked better, but
3D expression and morph target handling remains device-sensitive.

Important files:

```text
android/app/src/main/java/com/example/smartphonapptest001/ui/component/GuardianMixedRealityStage.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/component/GuardianAvatar3DStage.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/screen/ChatScreen.kt
```
