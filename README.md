# URL Radio Droid

Минималистичное Android-приложение для прослушивания интернет-радио по прямым streaming-ссылкам (HTTP/HTTPS).

## Возможности

- Добавление радиостанций с указанием названия и URL стрима
- Просмотр списка сохранённых станций
- Воспроизведение потоков через ExoPlayer
- Регулировка громкости через системный AudioManager
- Локальное хранение данных через Room Database

## Требования

- Android 8.0 (API 26) или выше
- Android Studio или совместимая IDE
- Gradle 8.2+

## Сборка

1. Клонируйте репозиторий или откройте проект в Android Studio
2. Синхронизируйте зависимости Gradle
3. Соберите проект: `./gradlew build`
4. Установите на устройство: `./gradlew installDebug`

## Использование

1. Запустите приложение
2. Нажмите кнопку "Добавить" (FAB)
3. Введите название станции и URL стрима (например: `http://stream.example.com:8000/stream`)
4. Нажмите "Сохранить"
5. Выберите станцию из списка для воспроизведения
6. Используйте кнопку Play/Stop для управления воспроизведением
7. Регулируйте громкость с помощью ползунка

## Технологии

- Kotlin
- AndroidX (AppCompat, Material Components)
- Room Database
- ExoPlayer
- ViewBinding
- Coroutines

## Структура проекта

```
app/src/main/
├── java/com/urlradiodroid/
│   ├── data/
│   │   ├── RadioStation.kt
│   │   ├── RadioStationDao.kt
│   │   └── AppDatabase.kt
│   └── ui/
│       ├── MainActivity.kt
│       ├── AddStationActivity.kt
│       ├── PlaybackActivity.kt
│       └── StationAdapter.kt
└── res/
    ├── layout/
    ├── values/
    └── ...
```

## Лицензия

Проект создан для личного использования.
