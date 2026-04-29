# Android migration list

Source project:

```text
C:\work\test001\smartphonapptest001
```

Target directory:

```text
C:\work\guardian_of_the_plants\android
```

## Copy

Copy these files and directories into `android/`.

```text
app/
gradle/
build.gradle.kts
settings.gradle.kts
gradle.properties
README.md
.gitattributes
.gitignore
```

## Do Not Copy

Do not copy generated files, local machine settings, caches, APK outputs, or Git history.

```text
.git/
.gradle/
.kotlin/
build/
app/build/
local.properties
*.apk
```

## Notes

- `local.properties` contains a local Android SDK path and should be recreated by Android Studio on each machine.
- APK files should be treated as build artifacts, not source code.
- Keep server, database, nginx, and Android source in the same repository, but keep their generated outputs separate.
- After migration, build from `android/` using the Gradle wrapper or Android Studio.

## Suggested Migration Command

Use a copy command that excludes generated directories. Review the result before committing.

```powershell
robocopy C:\work\test001\smartphonapptest001 C:\work\guardian_of_the_plants\android /E `
  /XD .git .gradle .kotlin build app\build `
  /XF local.properties *.apk
```

`robocopy` returns non-zero status codes for successful copies with changes, so read its summary rather than treating every non-zero code as failure.
