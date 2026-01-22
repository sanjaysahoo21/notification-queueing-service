# Code Structure and File Explanation - Notification Queueing Service

Ee file lo manam raasina Java code files yenti, avi em chestayi, enduku raasam anedi simple Tenglish lo chuddam.

Manaki main ga 2 projects unnai:

1. **API** (Request tiskuntundi)
2. **Worker** (Background lo pani chestundi)

---

## 🚀 1. API Project Files

Ee project Pani: User dagara nundi notification tiskoni, validate chesi, queue lo veyadam.

### 🎮 Controller Classes

**File:** `NotificationController.java`

- **Em chestundi:** Idi "Receptionist" laantidi. User request ni receive cheskuntundi.
- **Why:** Code lo ki entry point ide.
- **How:** User request pampagane, validate chesi, `NotificationService` ki pass chestundi. Pani aipoyaka "Response" istundi.

### 📦 DTO Classes (Data Transfer Object)

**File:** `NotificationRequest.java`

- **Em chestundi:** User pampinche data ni hold chestundi.
- **Why:** Direct ga Database objects (Entity) ni bayata vadakudadu. Security and clean code kosam.
- **How:** Indulo `email`, `message`, `subject` fields untai. `@Email`, `@NotBlank` validations ikkade raastam.

**File:** `NotificationMessage.java`

- **Em chestundi:** API nundi Worker ki Queue dwara pampinche "Letter" idhe.
- **Why:** Queue lo pettuadaniki simple format kavali.

### 🗄️ Entity Classes

**File:** `Notification.java`

- **Em chestundi:** Idi Database Table ki mirror image.
- **Why:** Java object ni Database row ga marchadaniki (ORM).
- **How:** `@Entity` annotation tho untundi. Indulo `id`, `status` (PENDING/SENT), `createdAt` lanti columns untai.

### 💾 Repository Interfaces

**File:** `NotificationRepository.java`

- **Em chestundi:** Database tho matladutundi.
- **Why:** Manam SQL queries rayakunda, direct ga `save()`, `findById()` methods vadukodaniki.
- **How:** Spring Data JPA interface idi. Automatic ga implementation ippistundi.

### ⚙️ Service Classes

**File:** `NotificationService.java`

- **Em chestundi:** Main Logic ekkade untundi ("Manager" laga).
- **Why:** Controller clean ga undali. Business logic antha service lo undali.
- **How:**
  1. DTO ni Entity ga marchutundi.
  2. Database lo `PENDING` ani save chestundi.
  3. `NotificationPublisher` ni pilichi, message ni Queue lo vestundi.

### 🚦 Rate Limiting Classes

**File:** `RatelimiterService.java`

- **Em chestundi:** Traffic Police.
- **How:** Redis lo count pettukuni chustundi. Limit datithe `false` istundi.

**File:** `RateLimitInterceptor.java`

- **Em chestundi:** Prathi request service daka vellaka munde checkpost laga aputundi.
- **How:** Request rakagane `RatelimiterService` ni adigi, allow cheyyocha leda ani decide chestundi.

### 📨 RabbitMQ Config and Publisher

**File:** `RabbitMQConfig.java`

- **Em chestundi:** Setup chestundi.
- **How:** Queue name `notification.queue`, Exchange name `notification.exchange` ani define chestundi.

**File:** `NotificationPublisher.java`

- **Em chestundi:** Queue lo message vesedi ide (Postman).
- **How:** `RabbitTemplate` use chesi message ni RabbitMQ ki "Shoot" chestundi.

### ⚠️ Exception Handler

**File:** `GlobalExceptionHandler.java`

- **Em chestundi:** Errors ni handle chestundi.
- **Why:** Code lo error vasthe, user ki weird stacktrace kanipinchakunda, neat ga error message chupinchadaniki.
- **How:** `400 Bad Request` or `500 Server Error` ni catch chesi json error istundi.

---

## 👷 2. Worker Project Files

Ee project Pani: Queue lo unna pani ni silent ga cheyadam.

### 📨 RabbitMQ Consumer

**File:** `NotificationConsumer.java`

- **Em chestundi:** Asalu pani manishi ("Worker") ide.
- **Why:** Queue lo padda messages ni process cheyyali kabatti.
- **How:**
  1. `@RabbitListener` tho eppudu queue ni vintune untundi.
  2. Message raagane receive cheskoni DB lo check chestundi.
  3. Status ni `SENT` ki update chestundi.

### 📦 DTO in Worker

**File:** `NotificationMessage.java`

- **Note:** API lo unna `NotificationMessage` copy ne ikkada kuda untundi. Endukante API pampinchina JSON ni Worker ardham cheskovali kada. Format same undali.

### 🗄️ Entity and Repository in Worker

**File:** `Notification.java` & `NotificationRepository.java`

- **Note:** Ivvi kuda API lo unnatte untai. Worker kuda same database table ni update cheyyali kabatti, deeniki kuda table access kavali.

### ⚙️ Configuration Classes

**File:** `RabbitMQConfig.java`

- **Em chestundi:** Worker side kuda JSON ni ardham cheskodaniki config kavali.
- **How:** `Jackson2JsonMessageConverter` ni add chestam. Ledante API pampina JSON ni Worker chadukoledu.

---

### 🔄 Summary Flow (Mottam ela work avtundi)

1. **User** → `NotificationController` ki request istadu.
2. `RateLimitInterceptor` aapi check chestundi ("Limit unda?").
3. `NotificationService` call ayyi, DB lo **"PENDING"** ani save chestundi.
4. `NotificationPublisher` adi tiskoni **RabbitMQ Queue** lo vestundi.
5. **API pani aipoyindi**, User ki "Accepted" ani cheptundi.
6. Background lo **Queue** nundi `NotificationConsumer` (Worker) message tiskuntundi.
7. Worker malli DB connect ayyi status ni **"SENT"** ani marustundi.

Anthe! Simple and Powerful architecture. 🚀
