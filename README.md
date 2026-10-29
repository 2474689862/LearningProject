# AI 学习助手（AI Study Assistant）

基于 **Jakarta EE + Struts 2 + Tomcat** 的错题管理与 AI 学习助手。核心目标是把"错题本"从一个静态记录工具，变成一个能识别、能讲解、能跟踪薄弱点的学习闭环。

> 定位：个人学习项目 / 教学演示。不是生产级系统，**请勿存放真实的付费 API Key 或他人隐私数据**。

---

## 亮点

| 能力 | 实现方式 |
|---|---|
| 错题拍照识别 | 图片预处理（EXIF 纠正、分辨率归一化、长图切带）→ 本机 Ollama 视觉模型转写（公式输出 LaTeX）→ 文字模型结构化 JSON |
| 公式显示 | 服务端输出 LaTeX，前端用本地 KaTeX（离线 vendor）渲染，无 CDN 依赖 |
| 题型覆盖 | 11 种模板：选择 / 判断 / 填空 / 简答 / 解答 / 阅读 / 实验 / 写作 / 听力 / 翻译 / 计算 |
| 学科覆盖 | 高中九门学科，预置 66 个分类（首次启动自动灌入） |
| AI 服务可切换 | 本地 Ollama / 云端 API / 自动 / 纯本地规则，设置页实时切换，**改完立即生效，不用重启 Tomcat** |
| 配置热生效 | 优先级：环境变量 > 设置页（`t_setting` 表）> `ai.properties` > 代码默认值 |
| API Key 保护 | Key 以 AES-GCM 加密后入库（`enc:v1:` 前缀），界面上只回显后四位 |
| 识别可信度校验 | 识别后可选联网检索核对（默认免 Key 档 / AnySearch 档），命中结果作为证据回注 |
| AI 对话带个人数据 | 问答时注入该生的错题、学科分布、薄弱题型，回答不是通用模板 |
| 部署零手工 | 启动时自动建库、建表、灌种子数据、创建演示账号 |

---

## 技术栈

- **JDK 17+**（编译目标 17，实测在 JDK 21 运行）
- **Apache Tomcat 11.x**（Jakarta Servlet 6.1，注意不是 Tomcat 9/10）
- **Struts 2.7（Struts 7.3.0）**，`struts.parameters.requireAnnotations=false`
- **MySQL 8**
- 前端：原生 HTML/CSS/JS，无框架、无构建步骤
- AI：本机 Ollama（qwen3 / qwen2.5vl）或 OpenAI 兼容云端接口（主要适配 DeepSeek）

---

## 快速开始

### 0. 准备

```bash
git clone <repo-url>
cd ai-study-assistant
```

需要：JDK 17+、Maven 3.8+、MySQL 8（本地已启动）、Tomcat 11.x。

### 1. 配置数据库

复制模板并按本机情况修改（**`db.properties` 已被 git 忽略，可以放心写密码**）：

```bash
# Windows
copy src\main\resources\db.properties.example src\main\resources\db.properties
# macOS / Linux
cp src/main/resources/db.properties.example src/main/resources/db.properties
```

也可以不改文件，改用环境变量：

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/ai_study?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false&allowMultiQueries=true
DB_USER=root
DB_PASSWORD=你的密码
```

> 应用启动时会执行 `CREATE DATABASE IF NOT EXISTS ai_study`，所以数据库账号需要有**建库权限**。
> 若账号权限不足，请先手动建库：
> ```sql
> CREATE DATABASE ai_study DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
> ```
> 表结构和种子数据会在首次启动时自动创建，无需手动执行 SQL。

### 2. 打包

```bash
mvn clean package -DskipTests
```

产物：`target/ai-study.war`

### 3. 部署

**方式 A：手动丢进 Tomcat**

```bash
cp target/ai-study.war <TOMCAT_HOME>/webapps/
<TOMCAT_HOME>/bin/startup.sh      # Windows: startup.bat
```

等待 `logs/catalina.out` 出现 `[AI-Study] 数据库初始化完成` 后访问：

```
http://localhost:8080/ai-study/
```

**方式 B：IDEA**

1. `Run → Edit Configurations → + → Tomcat Server → Local`，选中本机 Tomcat 11
2. `Deployment` 里添加 `Artifact: ai-study:war exploded`，Application context 建议填 `/ai-study`
3. `Server` 标签里确认 HTTP port 未被占用（默认 8080 常被 VMware / 其他服务占用，可改 8088）
4. 启动后访问 `http://localhost:8088/ai-study/`

### 4. 登录

| 账号 | 密码 | 角色 |
|---|---|---|
| `student` | `123456` | 学生（演示数据） |
| `admin` | `admin123` | 管理员 |

---

## AI 服务配置

进入网站 **设置 → AI 服务**，四种模式：

| 模式 | 说明 | 需要什么 |
|---|---|---|
| `ollama` | 全部走本机 Ollama | 本机装好 Ollama + 拉取模型 |
| `cloud` | 文字任务走云端 API | 云端 Base URL + API Key |
| `auto` | 有云端 Key 用云端，否则探测 Ollama，再否则纯本地规则 | 视配置而定 |
| `local` | 只用本地规则模板，不调用任何模型 | 无 |

### 本机 Ollama（推荐，免费）

设置页「AI 服务 → 本地模型方案」提供两种选择，点按钮即自动填好模型名并通过「保存并生效」提交：

| 方案 | 模型 | 磁盘 | 适合谁 |
|---|---|---|---|
| **完整**（默认） | 文字 `qwen3:8b` + 识图 `qwen2.5vl:7b` | 约 11 GB | 显存 ≥ 8 GB；想要更好的问答、讲解、总结质量 |
| **轻量** | 文字与识图都用 `qwen2.5vl:7b` | 约 6 GB | 核显 / 6–8 GB 显存 / 只想先跑通识图 |

```bash
# 完整方案
ollama pull qwen3:8b
ollama pull qwen2.5vl:7b

# 轻量方案（只拉一个）
ollama pull qwen2.5vl:7b
```

两种方案的差别：

- **完整**：文字任务由纯文本模型负责，指令遵循和中文表达明显更强；两个模型体积相加约 11 GB，8 GB 显卡靠「同一时刻只加载一个模型」互斥运行，切换时要重新加载，首次响应会有几秒到十几秒的等待。
- **轻量**：qwen2.5vl 本身就是多模态模型，也能处理纯文字，所以只用一个模型即可覆盖两条链路；显存/磁盘压力最小、切换零开销，但文字任务的准确度与长文总结质量弱于 qwen3，复杂解题步骤容易简化。

Windows 下可直接双击项目根目录的 `start-ollama.bat`（会检查 11434 端口并按需启动）。
模型名如与本机不同，在设置页「本地区块」里改即可。

> **识图固定走本机 Ollama**，因为适配问题走云端容易出错。选云端模式时，文字任务走云端、拍照识图仍走本机；本机 Ollama 没启动时识图会给出明确提示而不是报错崩溃。

### 云端 API（DeepSeek 适配最好）

设置页选「云端」，厂商选 DeepSeek：

- Base URL：`https://api.deepseek.com/v1`
- 模型：`deepseek-flash`（快、便宜）或 `deepseek-v4-pro`（更强）
  - 老账号可能只开了 `deepseek-chat`；报 `model not found` 时换这个试
- API Key：`sk-...`，保存后加密入库，界面只显示后四位

**厂商适配等级**（设置页会直接提示）：

| 等级 | 厂商 |
|---|---|
| 完整适配 | DeepSeek、自定义 OpenAI 兼容网关 |
| 可选但受限 | OpenAI、Moonshot、智谱、通义兼容、硅基流动（协议兼容，但鉴权细节/参数/JSON 输出未逐个验证） |
| 未适配 | Anthropic、Gemini、Azure、百炼（非 OpenAI 协议，直接调用大概率失败） |

> 其它厂商，不作为支持承诺。选未适配厂商保存前会二次确认。

---

## 目录结构

```
src/main/java/com/aistudy/
  action/      Struts Action（Auth / Question / Upload / Ai / Settings ...）
  ai/          AiConfig（配置三层回退）、AiService（模型调用）、联网校验
  config/      AppSettings —— t_setting 内存覆盖层（热生效）
  dao/         JDBC DAO（无连接池，学习用途）
  listener/    AppInitListener —— 启动建库建表灌种子
  model/       实体与 PageResult
  util/        DbUtil / PasswordUtil / CryptoUtil / ImagePreprocessor

src/main/resources/
  ai.properties          AI 服务默认配置（可被设置页覆盖）
  db.properties.example  数据库配置模板（复制为 db.properties 使用）
  db/schema.sql          建表脚本（启动时自动执行）
  db/seed.sql            学科分类与题型种子
  struts.xml             路由表

src/main/webapp/
  *.html                 9 个页面：登录入口 index / 首页 home / 错题 questions / 上传 upload / 分类 categories / AI 助手 ai / 总结 summary / 对话 chat / 设置 settings
  js/                    api.js（请求封装）qtype.js（题型模板）latex.js（KaTeX）settings.js
  css/app.css
  vendor/katex/          本地 KaTeX，离线可用
  WEB-INF/web.xml        Struts 过滤器、UploadServlet 映射（/uploads/*）
```

> **图片存放在 Web 应用之外**，默认 `${user.home}/ai-study-uploads`（Windows 即 `C:\Users\<你>\ai-study-uploads`）。
> 可用环境变量 `APP_UPLOADS_DIR` 或 JVM 参数 `-Dapp.uploads.dir=...` 改到任意目录。
> 因此 `mvn clean`、重新部署 war 都不会丢图；`/uploads/*` 由 `UploadServlet` 代理读取。

---

## 常见问题

**Q：启动报 `BindException: Address already in use`**
Tomcat 端口被占用。Windows 上 8080 常被 VMware NAT (`vmnat.exe`) 占用，改成 8088 即可；同时把 Shutdown port 改成一个没被用的（如 8019）。查占用：

```powershell
netstat -ano | findstr :8080
```

**Q：访问根路径 404**
Context path 不是 `/`。war 包名是 `ai-study`，所以地址是 `http://localhost:8080/ai-study/`；IDEA 里 exploded 部署通常是 `/ai-study` 或 `/ai_study_assistant_war_exploded`，以运行日志里的 "Deployment...context path" 为准。

**Q：登录时提示"请输入账号密码"**
确认 `db.properties` 里的库名与连接串一致，且启动日志出现 `数据库初始化完成`。若数据库连不上，启动日志会打印 `[AI-Study] 数据库初始化失败`。

**Q：云端报 `model not found` / 401**
- `model not found`：模型名不在该账号可用列表，换 `deepseek-chat` 试
- `401 / 403`：Key 无效或余额不足，到 DeepSeek 控制台确认

**Q：识图报"图片识别固定使用本机 Ollama"**
这是设计行为。启动 Ollama（`start-ollama.bat` 或 `ollama serve`），并确认已 `ollama pull qwen2.5vl:7b`。

**Q：重新部署 war / 执行 mvn clean 后错题图片会丢吗**
不会。图片存在 Web 应用之外（默认 `${user.home}/ai-study-uploads`，可用 `APP_UPLOADS_DIR` 指定），与构建产物和 war 无关。重装系统或迁移时把该目录一起复制过去即可。

---

## 安全说明

- `src/main/resources/db.properties` **已被 git 忽略**，请只在本机使用；提交前确认 `git status` 里没有它。
- 注意：`mvn package` 会把本机的 `db.properties` 一起打进 `target/ai-study.war`。**这个 war 只能自己部署，不要分发给别人**（对方可从中读到你的数据库密码）。
- API Key 使用 AES-GCM 加密入库。密钥由 `APP_SECRET` 派生；**未设置时使用代码内的演示密钥**，这意味着任何人下载源码都能解密自己库里的密文。真实使用请设置：

  ```bash
  APP_SECRET=<足够长的随机字符串>
  ```

- 所有登录用户都可修改全局设置（含 AI 服务），这是为对比实验做的简化，**不要部署到公网供多用户使用**。

---

## License

建议按 MIT 使用（如需请自行添加 `LICENSE` 文件）。
