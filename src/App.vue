<script setup>
import {
  ArrowRight,
  CheckCircle2,
  Code2,
  Download,
  ImagePlus,
  Images,
  KeyRound,
  MessageSquare,
  RefreshCw,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Tags,
} from '@lucide/vue'

const repoUrl = 'https://github.com/glosc-ai/Glosc-Images'
const releaseUrl = `${repoUrl}/releases`
const keyUrl = 'https://one.gloscai.com/keys'

const features = [
  {
    icon: SlidersHorizontal,
    title: '工程式生图',
    text: '提示词、负向提示词、尺寸、质量、数量、种子和模型选择都在同一处完成，适合稳定复用的创作流程。',
  },
  {
    icon: MessageSquare,
    title: '对话式创作',
    text: '通过自然语言持续描述画面需求，保留上下文和图片消息，让修改和再生成更接近真实沟通。',
  },
  {
    icon: ImagePlus,
    title: '图片编辑与变换',
    text: '围绕已有图片继续局部重绘、变体、扩图和增强，把一次生成延展成完整素材资产。',
  },
  {
    icon: Images,
    title: '本地图片库',
    text: '生成结果保存到本地图片库，支持收藏、标签、搜索、来源筛选和详情元数据查看。',
  },
  {
    icon: KeyRound,
    title: '兼容 API 接入',
    text: '默认连接 Glosc AI One，也支持 OpenAI 兼容服务商和自定义 Base URL，方便接入团队已有渠道。',
  },
  {
    icon: ShieldCheck,
    title: '密钥本地保护',
    text: 'API Key 使用 Android Keystore 加密保存，不写入数据库和日志，敏感配置留在设备本地。',
  },
]

const workflow = [
  '保存 Glosc AI Key，获取可用图片模型列表。',
  '用参数表单或对话描述图片需求。',
  '生成、继续编辑、收藏，并沉淀到本地图片库。',
]

const screenshots = [
  {
    src: '/images/app-screens/generate.jpg',
    title: '工程式生图',
    text: '首屏直接完成模型、尺寸、质量、数量、种子和提示词输入。',
  },
  {
    src: '/images/app-screens/chat.jpg',
    title: '对话创作',
    text: '用自然语言继续描述画面需求，适合灵感探索和迭代。',
  },
  {
    src: '/images/app-screens/library.jpg',
    title: '本地图片库',
    text: '生成结果按作品保存，可筛选、搜索、收藏和继续编辑。',
  },
  {
    src: '/images/app-screens/settings.jpg',
    title: 'API 设置',
    text: '连接 Glosc AI One，获取模型列表，并选择稳定可用的默认模型。',
  },
]

const specs = [
  ['默认渠道', 'Glosc AI One'],
  ['接口形态', 'OpenAI-compatible Images API'],
  ['目标平台', 'Android native'],
  ['数据策略', '本地图片库和加密密钥'],
]
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <a class="brand" href="#top" aria-label="Glosc Images 首页">
        <img src="/images/glosc-logo.png" alt="" />
        <span>Glosc Images</span>
      </a>

      <nav class="nav-links" aria-label="主导航">
        <a href="#features">功能</a>
        <a href="#screens">界面</a>
        <a href="#workflow">流程</a>
        <a href="#privacy">安全</a>
        <a href="#download">获取</a>
      </nav>
    </header>

    <main id="top">
      <section class="hero-section" aria-labelledby="hero-title">
        <div class="hero-scene" aria-hidden="true">
          <div class="scene-grid"></div>
          <figure class="screen-card screen-card-main">
            <img src="/images/app-screens/generate.jpg" alt="" />
          </figure>
          <figure class="screen-card screen-card-side">
            <img src="/images/app-screens/library.jpg" alt="" />
          </figure>
        </div>

        <div class="hero-copy">
          <div class="eyebrow">
            <Sparkles :size="16" />
            Android AI image studio
          </div>
          <h1 id="hero-title">Glosc Images</h1>
          <p>
            面向移动端创作者的 AI 生图应用。把参数化生成、对话式创作、图片编辑和本地图片库放在一个清晰的工作台里。
          </p>
          <div class="hero-actions">
            <a class="button primary" :href="releaseUrl">
              <Download :size="18" />
              获取 Android 版
            </a>
            <a class="button secondary" :href="keyUrl">
              获取 API Key
              <ArrowRight :size="18" />
            </a>
          </div>
        </div>
      </section>

      <section class="intro-band" aria-label="产品定位">
        <div class="intro-inner">
          <div>
            <span class="section-kicker">为什么需要它</span>
            <h2>从一次生成，到持续可管理的图片资产</h2>
          </div>
          <p>
            Glosc Images 不是只发起一次请求的工具。它把模型选择、生成参数、编辑记录、收藏标签和历史检索连成一个移动端创作流程，适合日常内容制作、设计草图和运营素材探索。
          </p>
        </div>
      </section>

      <section id="features" class="content-section">
        <div class="section-heading">
          <span class="section-kicker">核心功能</span>
          <h2>覆盖 AI 图片创作的主要步骤</h2>
        </div>

        <div class="feature-grid">
          <article v-for="feature in features" :key="feature.title" class="feature-card">
            <component :is="feature.icon" :size="22" />
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.text }}</p>
          </article>
        </div>
      </section>

      <section id="screens" class="screenshots-section">
        <div class="section-heading">
          <span class="section-kicker">真实界面</span>
          <h2>从 App 直接截取的核心页面</h2>
        </div>

        <div class="screenshot-grid">
          <figure v-for="item in screenshots" :key="item.title" class="screenshot-item">
            <img :src="item.src" :alt="item.title" loading="lazy" />
            <figcaption>
              <strong>{{ item.title }}</strong>
              <span>{{ item.text }}</span>
            </figcaption>
          </figure>
        </div>
      </section>

      <section id="workflow" class="workflow-section">
        <div class="section-heading">
          <span class="section-kicker">工作流</span>
          <h2>三步开始创作</h2>
        </div>

        <div class="workflow-layout">
          <ol class="workflow-list">
            <li v-for="item in workflow" :key="item">
              <CheckCircle2 :size="20" />
              <span>{{ item }}</span>
            </li>
          </ol>

          <div class="workflow-panel">
            <div class="panel-title">
              <Settings :size="20" />
              <strong>API 设置</strong>
            </div>
            <div class="settings-lines">
              <div v-for="[label, value] in specs" :key="label">
                <span>{{ label }}</span>
                <strong>{{ value }}</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="privacy" class="privacy-section">
        <div class="privacy-copy">
          <span class="section-kicker">安全与控制</span>
          <h2>关键配置保留在用户设备里</h2>
          <p>
            应用默认不会把 API Key 写入 Room 或日志。生成图片、任务状态和标签在本地管理，用户可以按来源、模型和标签持续整理自己的素材。
          </p>
        </div>
        <div class="privacy-points">
          <div>
            <KeyRound :size="22" />
            <strong>Key 加密保存</strong>
            <span>Android Keystore 保护服务商密钥。</span>
          </div>
          <div>
            <Tags :size="22" />
            <strong>素材可检索</strong>
            <span>通过收藏、标签和搜索快速找回结果。</span>
          </div>
          <div>
            <RefreshCw :size="22" />
            <strong>可替换服务</strong>
            <span>兼容 OpenAI 风格接口和自定义渠道。</span>
          </div>
        </div>
      </section>

      <section id="download" class="download-section">
        <div>
          <span class="section-kicker">获取项目</span>
          <h2>下载最新 Android 包，或查看源码</h2>
          <p>
            GitHub Pages 会从当前 `web` 分支自动构建并发布此官网。Android 应用发布包通过 GitHub Releases 分发。
          </p>
        </div>
        <div class="download-actions">
          <a class="button primary" :href="releaseUrl">
            <Download :size="18" />
            查看 Releases
          </a>
          <a class="button secondary" :href="repoUrl">
            <Code2 :size="18" />
            GitHub 仓库
          </a>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <span>Glosc Images</span>
      <a :href="repoUrl">github.com/glosc-ai/Glosc-Images</a>
    </footer>
  </div>
</template>
