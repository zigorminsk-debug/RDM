# Remote Desktop Manager (RDM) для Android

Современное нативное Android-приложение для централизованного управления подключениями к удаленным серверам Windows (RDP) с группировкой по клиентам/компаниям.

![RDM Android](app/src/main/res/drawable/ic_rdm_logo.xml)

---

## 📱 Основные возможности приложения

### 1. 🏢 Иерархия «Клиенты → Карточки серверов»
- **Список клиентов / компаний**: создание, редактирование, удаление клиентов, цветовая маркировка и счетчик активных серверов.
- **Карточки серверов клиента**: при клике на клиента открывается список всех его серверов.
- **Глобальный поиск**: быстрый поиск по названию клиента, сервера, IP-адресу, логину или домену.

### 2. ⚡ Быстрый запуск RDP-подключения (Клик на карточку)
- **Нажатие (клик) на карточку сервера** запускает подключение через RDP-клиент.
- Поддержка:
  - **Microsoft Remote Desktop** (`com.microsoft.rdc.android`)
  - **aFreeRDP** (`com.freerdp.afreerdp`)
  - Генерация и передача файла `.rdp` через защищенный `FileProvider` в любое RDP-приложение
  - Протоколы `rdp://` и `ms-rd://`
  - Предложение установки официального клиента из Google Play, если RDP-клиент не установлен.

### 3. ✏️ Редактирование карточки сервера (Долгое нажатие)
- **Долгое нажатие (long press) на карточку сервера** открывает модальное окно редактирования со всеми полями:
  - 🏷️ **Название сервера** (Name)
  - 🌐 **IP / Хост** (IP Address / Hostname)
  - 🔌 **Порт** (Port, по умолчанию `3389`)
  - 🏢 **Домен** (Domain, необязательно)
  - 👤 **Логин** (Username)
  - 🔑 **Пароль** (Password, с кнопкой скрытия/показа `••••••` и быстрым копированием)
  - 📝 **Заметки** (Notes)
  - 🖥️ **Разрешение экрана** (1920x1080, 1280x720 и др.)
  - 🔊 **Перенаправление звука** (на устройство, на сервере, отключить)
  - 🛡️ **Сессия администратора** (`/admin` консольный режим)
  - 🎨 **Цветовая метка**

### 4. 🛠️ Дополнительные функции
- **Быстрое копирование**: кнопки копирования `IP:порт`, `Логин`, `Пароль` прямо с карточки.
- **TCP Ping (Тест доступности)**: проверка доступности RDP-порта в реальном времени (индикаторы Онлайн 🟢 / Офлайн 🔴).
- **Дублирование серверов**: быстрое создание копии существующего сервера.
- **Резервное копирование (JSON)**: полный экспорт и импорт базы клиентов и серверов.
- **Демо-данные**: возможность загрузки демонстрационных клиентов и серверов в один клик.

---

## 🏗️ Архитектура и стек технологий

- **Язык**: Kotlin 1.9+
- **UI**: Jetpack Compose + Material 3 (Material You, динамические цвета, поддержка светлой и темной темы)
- **База данных**: AndroidX Room (SQLite) + Kotlin Coroutines & Flow (реактивное обновление данных)
- **Архитектура**: MVVM (Model-View-ViewModel) + Repository Pattern + Clean Architecture
- **Навигация**: Navigation Compose
- **Интеграция с RDP**: Android Intent System, FileProvider `.rdp`, MIME-типы `application/x-rdp`

---

## 🚀 CI/CD и автосборка на GitHub Actions

В репозитории настроен автоматический пайплайн сборки (`.github/workflows/build.yml`):

### 🔑 Постоянный ключ подписи (Release Keystore)
- Приложение подписывается **постоянным ключом** (`keystore/release.keystore`), действительным на 50 лет (до 2076 года).
- Это гарантирует возможность **бесшовного обновления поверх** установленного приложения без потери данных и без конфликта сертификатов (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- Параметры ключа зафиксированы в `keystore/keystore.properties`:
  - **Alias**: `rdmkey`
  - **Password**: `rdm_release_key_2026`

### 🔢 Автоматическая нумерация сборок
- Номер каждой сборки динамически вычисляется на основе `${{ github.run_number }}`:
  - `versionCode` = `1000 + github.run_number`
  - `versionName` = `1.0.${github.run_number}`
- Каждая новая сборка автоматически получает уникальный инкрементируемый номер версии.

### 📦 Автоматический релиз и артефакты
- При каждом `push` в ветку `main`:
  1. GitHub Actions компилирует подписанный `Release APK` и `Debug APK`.
  2. Артефакты загружаются в `Actions Artifacts`.
  3. Автоматически создается **GitHub Release** с прикрепленным файлом `RDM-v1.0.X-signed.apk`.

---

## 💻 Локальная сборка

Для сборки проекта на локальном компьютере:

```bash
# Клонировать репозиторий
git clone https://github.com/zigorminsk-debug/RDM.git
cd RDM

# Сборка подписанного Release APK
./gradlew assembleRelease

# Сборка Debug APK
./gradlew assembleDebug
```

Собранные файлы APK будут находиться в папке:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 Структура проекта

```
RDM/
├── .github/
│   └── workflows/
│       └── build.yml               # GitHub Actions CI/CD автосборка и релиз
├── app/
│   ├── build.gradle.kts           # Конфигурация модуля Android, версионирование, подпись
│   ├── proguard-rules.pro         # Правила оптимизации ProGuard
│   └── src/main/
│       ├── AndroidManifest.xml    # Манифест с FileProvider и RDP запросами
│       ├── java/com/rdm/remote/desktop/manager/
│       │   ├── MainActivity.kt    # Главная Activity с Jetpack Compose NavHost
│       │   ├── RdmApplication.kt  # Инициализация БД и демо-данных
│       │   ├── data/
│       │   │   ├── database/      # Room Database, ClientDao, ServerDao
│       │   │   ├── model/         # ClientEntity, ServerEntity
│       │   │   └── repository/   # RdmRepository (CRUD, JSON бэкап, TCP Ping)
│       │   ├── ui/
│       │   │   ├── theme/         # Material 3 Тема, Шрифты, Цвета
│       │   │   ├── navigation/    # Маршруты экранов
│       │   │   ├── viewmodel/     # MainViewModel (состояние, логика)
│       │   │   ├── screens/       # ClientListScreen, ServerListScreen, AllServersScreen, SettingsScreen
│       │   │   └── components/    # ClientCard, ServerCard, RdpLaunchDialog, EditDialogs, SearchBar
│       │   └── utils/
│       │       ├── RdpLauncher.kt # Генерация .rdp, запуск RDP Intent, проверка клиентов
│       │       ├── JsonBackupUtils.kt # Экспорт / импорт JSON
│       │       └── SampleData.kt  # Демонстрационные данные
│       └── res/                   # Ресурсы, иконки, темы, локализация (RU/EN)
├── keystore/
│   ├── release.keystore           # Постоянный ключ подписи (PKCS12 RSA-4096)
│   └── keystore.properties        # Конфигурация ключа подписи
├── build.gradle.kts               # Корневой скрипт сборки Gradle
├── settings.gradle.kts            # Настройки проекта
├── gradle.properties              # Настройки JVM и AndroidX
└── README.md
```

---

## 🔒 Безопасность

- Пароли хранятся в локальной зашифрованной / изолированной базе данных приложения Room.
- Поддерживается скрытие паролей с маскировкой `••••••` и переключателем видимости.
- Доступен экспорт и импорт конфигурации для безопасного переноса между устройствами.
