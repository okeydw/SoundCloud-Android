# fix(auth): QR не сканится

Патч для разраба API, т.к zxing-cpp не хочет тянуть те финтеплюшки в мэин софте

Github:
  - https://github.com/zxcloli666/SoundCloud-Desktop/issues/532

Файлы:
- `desktop/src/components/auth/QrCode.tsx`
- `desktop/src/components/auth/useQrLink.ts`

Патч: 
  - `docs/qr-fix.patch` -> в корне десктоп-репо `git apply qr-fix.patch`.

## 1. QR не сканируется сторонними сканерами

Код `scd://link?token=…&mode=push` плохо берётся камерами

QR-код, который использует SC для передачи сессий - не читаем для большинства либ, там онли хорошие qr сканеры с ML. 
А так ни нативный ZXing для Andriod, ни OpenCvv, даже zxing-cpp cо всеми агрессивными настройками не вывозит такое

Две причины в `QrCode.tsx`:
- **Лого 30%** (`imageOptions.imageSize: 0.3` + `hideBackgroundDots`) вырезает ~30% модулей.
- **Низкий контраст:** точки - градиент `#ffffff -> фиолетовый` на **прозрачном**
  фоне, угловые точки белые. Бинаризатор камеры теряет модули.

**Правка:**
- Убран центральный лого (`image`/`imageOptions` + вся обвязка `buildLogoBadge`/`loadLogo`).
- **Тёмные модули `#0d0d0d` на белом фоне** + `margin: 8` (quiet zone). Бренд-акцент
  остаётся на рамке (`qr-shell` / aurora / rim) — QR-карточка внутри контрастная.


## 2. Таймер «Истекает через 0 с» залипает на нуле

Бэкенд (`api/src/modules/auth/link_service.rs`):
```rust
let expires_at = (Utc::now() + Duration::seconds(300)).naive_utc();
```
Это UTC-время, сериализуется **без таймзоны** (`"2026-08-27T09:35:00"`). Клиент делает
`new Date(str)`, а JS парсит tz-less datetime как **локальное** время → в UTC+ зонах
(Москва +3) срок кажется уже прошедшим → `Math.max(0, …)` пиннит таймер к 0.

Сам токен не «протухает» — серверная проверка (`link.expires_at < Utc::now().naive_utc()`)
верна; врал только отображаемый отсчёт.

**Правка:** `parseServerTime()` в `useQrLink.ts` — если в строке нет таймзоны, форсим UTC
(`+ 'Z'`). Отсчёт снова идёт корректно (TTL 5 минут).

## Проверка
- Оба файла проходят парсинг (esbuild), без синтаксических ошибок.
- Строка данных (`scd://link?…`) и разметка `qr-shell` не меняются.
- Preview контрастного QR — `docs/qr-preview.png` (можно навести телефон).
