# Android Identity

Current user-facing app name:

```text
Plant Guardian
```

Current Android `applicationId`:

```text
com.ideatest0409.guardianplants
```

The Kotlin source package is still:

```text
com.example.smartphonapptest001
```

That source package can be renamed later, but it is intentionally left in
place for now to avoid a large refactor across every Kotlin file. The published
Android identity is controlled by `applicationId`.

Changing `applicationId` makes Android treat the APK as a different installed
app. Existing local app data under the old id will not automatically migrate.
