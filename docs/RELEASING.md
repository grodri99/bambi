# Publicar una actualización

## Cómo se publica Bambi ahora

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts` y haz push a `main` con el tag `vX.Y.Z`.
2. Compila el APK público **sin claves**: crea en la raíz un archivo `.public_build` con el contenido `1` (está en
   `.gitignore`) y compila la release. Así no lleva ni las claves iniciales, ni el secreto de Trakt, ni la configuración
   de Ithil, aunque estén en `local.properties`. Después pon `0` en `.public_build` para volver a las compilaciones
   de casa.
3. Crea el Release `vX.Y.Z` en GitHub y adjunta ese APK. El actualizador de la app busca `Bambi.apk` y, si no lo
   encuentra, cualquier `.apk` del Release.

## Workflow automático (opcional, solo a mano)


El workflow `.github/workflows/release.yml` publica un GitHub Release con el asset `Bambi.apk`, que es el nombre que busca el actualizador de la app.

## Requisitos

En **Settings → Secrets and variables → Actions**:

- `RELEASE_KEYSTORE_BASE64`: keystore de release codificado en Base64.
- `RELEASE_STORE_PASSWORD`: contraseña del keystore.
- `RELEASE_KEY_ALIAS`: alias de la clave.
- `RELEASE_KEY_PASSWORD`: contraseña de la clave.
- Variable `TRAKT_CLIENT_ID`: Client ID de la aplicación Trakt.
- Variable `TRAKT_REDIRECT_URI`: Redirect URI registrada en Trakt.

El build usa `seedKeys=false`: no incorpora claves personales de TMDB, Real-Debrid o SubDL.

## Publicar

1. Incrementa `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Haz commit y push de ese cambio a `main`.
3. Crea un tag con exactamente la versión precedida por `v`:

   ```bash
   git tag v1.0.7
   git push origin v1.0.7
   ```

El workflow comprueba que `v1.0.7` coincide con `versionName = "1.0.7"`, ejecuta los tests, compila y firma el APK y publica la release. También se puede iniciar manualmente desde Actions indicando un tag existente.

La clave de firma debe ser siempre la misma. Sin ella, Android rechazará la actualización sobre instalaciones anteriores. Conserva una copia de seguridad del keystore y sus contraseñas fuera de GitHub.
