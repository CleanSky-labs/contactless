# Store Metadata (Fastlane Format)

Este directorio contiene los assets y metadata para publicar en múltiples stores.

## Estructura

```
fastlane/
├── metadata/
│   └── android/
│       ├── en-US/           # Inglés (default)
│       ├── es-ES/           # Español
│       ├── pt-BR/           # Portugués (Brasil)
│       ├── ar/              # Árabe
│       ├── fa/              # Persa (Irán)
│       ├── ru-RU/           # Ruso
│       ├── tr-TR/           # Turco
│       ├── zh-CN/           # Chino simplificado
│       ├── hi-IN/           # Hindi
│       └── fr-FR/           # Francés
```

## Assets de texto (✅ Completados)

Cada directorio de idioma contiene:
- `title.txt` - Nombre de la app (max 30 caracteres)
- `short_description.txt` - Descripción corta (max 80 caracteres)
- `full_description.txt` - Descripción completa (max 4000 caracteres)

## Assets visuales (⏳ Pendientes)

### Requeridos para todos los stores

| Asset | Tamaño | Formato | Estado |
|-------|--------|---------|--------|
| **App Icon** | 512x512 px | PNG | ✅ `icon.png` existe |
| **Feature Graphic** | 1024x500 px | PNG/JPG | ⏳ Crear |
| **Screenshots** (min 2) | 1080x1920 px | PNG | ⏳ Crear |

### Screenshots recomendados

Crear 5-8 screenshots mostrando:

1. **Pantalla de bienvenida** - "Your keys, your funds"
2. **Modo Pagar** - Esperando NFC
3. **Solicitud de pago** - Mostrando monto y confirmar
4. **Pago exitoso** - Animación de éxito
5. **Modo Cobrar** - Teclado de monto
6. **Historial** - Lista de transacciones
7. **Configuración** - Opciones de red/privacidad
8. **Stealth Wallet** - Balance privado

### Feature Graphic (Banner)

Diseño sugerido para 1024x500:
```
┌─────────────────────────────────────────────┐
│                                             │
│   [Logo]  CleanSky                          │
│                                             │
│   Contactless Crypto Payments               │
│                                             │
│   🔒 Self-custody  📱 NFC  🌐 70+ languages │
│                                             │
└─────────────────────────────────────────────┘
```

## Publicación por Store

### Google Play
```bash
fastlane supply --track production
```

### Aptoide
1. Dashboard: https://www.aptoide.com/page/publishers
2. Upload APK + metadata manual
3. O usar API:
```bash
curl -X POST "https://ws75.aptoide.com/api/7/app/upload" \
  -H "Authorization: Bearer $APTOIDE_TOKEN" \
  -F "apk=@app/build/outputs/apk/release/app-release.apk"
```

### Huawei AppGallery
```bash
# Requiere Huawei Publishing API
# https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/agc-apigw-overview
```

### Samsung Galaxy Store
1. Seller Portal: https://seller.samsungapps.com
2. Upload manual con metadata

### Cafe Bazaar (Irán)
1. Dashboard: https://cafebazaar.ir/developers
2. Requiere cuenta iraní o intermediario

## Generar Screenshots automáticamente

Opción 1: Usar Android Emulator + scrcpy
```bash
# Grabar pantalla
scrcpy --record screenshot.mp4

# Extraer frames
ffmpeg -i screenshot.mp4 -vf "select=eq(n\,0)" -vsync vfr screenshot_%d.png
```

Opción 2: Usar Fastlane Screengrab
```bash
fastlane screengrab
```

## Checklist pre-publicación

- [ ] APK firmado con release keystore
- [ ] Versión y versionCode actualizados
- [ ] Icon 512x512 exportado
- [ ] Feature graphic 1024x500 creado
- [ ] Mínimo 2 screenshots por idioma
- [ ] Privacy policy URL configurada
- [ ] Categoría: Finance > Cryptocurrency
- [ ] Clasificación de edad completada
- [ ] Probar en dispositivos físicos
