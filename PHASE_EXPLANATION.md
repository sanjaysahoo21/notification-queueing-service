# Project Phases Explanation - Notification Queueing Service

Ee file lo manam **Notification Queueing Service** project ni ela build chesam, yentha parts ga divide chesam, inka prathi part (phase) enduku chesam anedi clear ga discuss cheddam.

---

## 🏗️ Phase 1: Project Setup and Docker Setup

**Why (Enduku):**
Project start chesetappudu system strong ga undali. Manam use chese tools like Database (Postgres), Queue (RabbitMQ), Cache (Redis) anni manam manual ga install cheskokunda, okesari run avvalante **Docker** avasaram.

**What (Em chesam):**

- Spring Boot project create chesam (API inka Worker ani 2 parts ga).
- `docker-compose.yml` file raasam.
- Indulo PostgreSQL, RabbitMQ, Redis images ni define chesam.
- Single command `docker compose up` tho anni run ayyela set chesam.

---

## 🗄️ Phase 2: Database Design and JPA Entity Setup

**Why (Enduku):**
Notification details (recipient, message, status) store cheyadaniki Database kavali. "Ee notification pampama leda?" ani telusukovalante DB lo record undali.

**What (Em chesam):**

- `Notification` ane table create chesam.
- Deentlo `recipient`, `message`, `status` (PENDING, SENT) lanti columns pettam.
- JPA Entity tho Database ki link chesam.

---

## ✅ Phase 3: DTO and Validation

**Why (Enduku):**
User pampinchina data correct ga undo ledo check cheyali. Email correct format lo unda? Message empty ga unda? ani telusukovali.

**What (Em chesam):**

- `NotificationRequest` (DTO) create chesam.
- Validations add chesam (`@Email`, `@NotBlank`).
- Tappu data vasthe "Bad Request" error vochela chesam.

---

## 🚦 Phase 4: Redis Rate Limiting

**Why (Enduku):**
Okaru spam cheyyakunda, ante oke user 1 minute lo 1000 requests pampinchakunda control cheyyali. "Traffic Police" laga work avvali.

**What (Em chesam):**

- **Redis** use chesi Rate Limiter pettam.
- User ID leda IP address batti count store chesam.
- 1 minute lo 5 requests kante ekkuva chesthe Block chesam (`429 Too Many Requests`).

---

## 📨 Phase 5: RabbitMQ Producer Setup

**Why (Enduku):**
Notifications direct ga pampithe time paduthundi. App slow avtundi. Anduke manam request tiskoni, ventane "Ok" cheppi, actual work ni **Queue** lo vestam.

**What (Em chesam):**

- **RabbitMQ** configure chesam (Exchange, Queue, Routing Key).
- User details ni JSON format lo Queue lo "Post" chese laaga Producer code raasam.

---

## 💾 Phase 6: Database Persistence Before Queue

**Why (Enduku):**
Queue lo message pette mundu, Database lo "PENDING" ani save cheyali. RabbitMQ down ayina kuda data miss avvakunda untundi.

**What (Em chesam):**

- Request vachina ventane DB lo save chesi ID generate chesam.
- Status = `PENDING`.
- Tarwata Queue ki pampincham.

---

## 👷 Phase 7: Worker Consumer and Idempotency

**Why (Enduku):**
Queue lo unna messages evaro okaru process cheyyali kada. Ade Worker job. Same message 2 times vasthe, 2 times send cheyyakunda chusukovali (Idempotency).

**What (Em chesam):**

- Separate **Worker** application create chesam.
- Idi Queue nundi messages "Read" chestundi (Consumer).
- Message tiskoni, DB lo status ni `PENDING` nundi `SENT` ki update chestundi.
- Already `SENT` unte malli process cheyyadu.

---

## 🔍 Phase 8: GET API for Notification Status

**Why (Enduku):**
User tana notification em ayyindo telusukovali kada. PENDING lo unda? SENT ayinda? ani.

**What (Em chesam):**

- `GET /api/notifications/{id}` API create chesam.
- Idi DB check chesi status return chestundi.

---

## 🏥 Phase 9: Health Check, Logging, Docker Polish

**Why (Enduku):**
Production lo pettinappudu app health bagundo ledo automatic ga teliyali. Logs unte error vachinappudu easy ga fix cheyochu.

**What (Em chesam):**

- Actuator add chesam (`/actuator/health`).
- Logs neat ga vochela configure chesam.
- Docker restart policies (automatic restart) pettam.

---

## 🧪 Phase 10: Testing, Swagger, README

**Why (Enduku):**
Code pani chestunda ani check cheyali. Documentation unte vere vallu easy ga use chestaru.

**What (Em chesam):**

- **Swagger UI** add chesam (API documentation kosam).
- `curl` commands tho manual testing chesam.
- `README.md` file lo project ela run cheyyalo clear ga raasam.

---

Ivi manam follow ayina phases. Step-by-step ga illu kattinattu, slow ga foundation nundi complete building varaku chesam! 🚀
