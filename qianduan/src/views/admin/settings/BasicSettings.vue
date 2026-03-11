<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { saveSystemSettings } from '../../../api/settings'
import { useSettingsStore, type SystemSettings } from '../../../stores/settings'
import { appendOperationLog } from '../../../utils/log'

type LogoField = 'adminLogo' | 'userHomeLogo' | 'titleLogo'

const settingsStore = useSettingsStore()
const MAX_LOGO_FILE_SIZE = 2 * 1024 * 1024

const adminLogoInputRef = ref<HTMLInputElement>()
const userHomeLogoInputRef = ref<HTMLInputElement>()
const titleLogoInputRef = ref<HTMLInputElement>()

const form = reactive({
  systemName: settingsStore.settings.systemName,
  systemDomain: settingsStore.settings.systemDomain,
  defaultPageSize: settingsStore.settings.defaultPageSize,
  enableLog: settingsStore.settings.enableLog,
  enableResumeDraft: settingsStore.settings.enableResumeDraft,
  adminLogo: settingsStore.settings.adminLogo || '',
  userHomeLogo: settingsStore.settings.userHomeLogo || '',
  titleLogo: settingsStore.settings.titleLogo || '',
  systemLogKeepDays: settingsStore.settings.systemLogKeepDays,
  systemLogKeepCount: settingsStore.settings.systemLogKeepCount,
  userLogKeepDays: settingsStore.settings.userLogKeepDays,
  userLogKeepCount: settingsStore.settings.userLogKeepCount
})

const logoPixelText = reactive<Record<LogoField, string>>({
  adminLogo: '未上传',
  userHomeLogo: '未上传',
  titleLogo: '未上传'
})

function getLogoRecommendSize(field: LogoField) {
  if (field === 'adminLogo') return '180 x 36 px'
  if (field === 'userHomeLogo') return '240 x 48 px'
  return '32 x 32 px'
}

function getLogoInputRef(field: LogoField) {
  if (field === 'adminLogo') return adminLogoInputRef.value
  if (field === 'userHomeLogo') return userHomeLogoInputRef.value
  return titleLogoInputRef.value
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result)
        return
      }
      reject(new Error('read_result_not_string'))
    }
    reader.onerror = () => reject(reader.error || new Error('read_logo_failed'))
    reader.readAsDataURL(file)
  })
}

function readImageSize(dataUrl: string) {
  return new Promise<{ width: number; height: number }>((resolve, reject) => {
    const image = new Image()
    image.onload = () =>
      resolve({
        width: image.naturalWidth,
        height: image.naturalHeight
      })
    image.onerror = () => reject(new Error('read_image_size_failed'))
    image.src = dataUrl
  })
}

async function updateLogoPixelText(field: LogoField, dataUrl: string) {
  if (!dataUrl) {
    logoPixelText[field] = '未上传'
    return
  }

  try {
    const size = await readImageSize(dataUrl)
    logoPixelText[field] = `${size.width} x ${size.height} px`
  } catch (error) {
    logoPixelText[field] = '已上传（尺寸解析失败）'
  }
}

async function syncLogoPixelText() {
  await Promise.all([
    updateLogoPixelText('adminLogo', form.adminLogo),
    updateLogoPixelText('userHomeLogo', form.userHomeLogo),
    updateLogoPixelText('titleLogo', form.titleLogo)
  ])
}

function triggerLogoUpload(field: LogoField) {
  getLogoInputRef(field)?.click()
}

function clearLogo(field: LogoField) {
  form[field] = ''
  logoPixelText[field] = '未上传'
}

async function handleLogoSelected(event: Event, field: LogoField) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''

  if (!file) return

  if (!file.type.startsWith('image/')) {
    alert('请上传图片格式文件')
    return
  }

  if (file.size > MAX_LOGO_FILE_SIZE) {
    alert('Logo 文件不能超过 2MB')
    return
  }

  try {
    const dataUrl = await readFileAsDataUrl(file)
    form[field] = dataUrl
    await updateLogoPixelText(field, dataUrl)
  } catch (error) {
    alert('读取 Logo 失败，请重试')
  }
}

async function handleSave() {
  const payload: SystemSettings = {
    ...settingsStore.settings,
    systemName: form.systemName.trim(),
    systemDomain: form.systemDomain.trim(),
    defaultPageSize: form.defaultPageSize,
    enableLog: form.enableLog,
    enableResumeDraft: form.enableResumeDraft,
    adminLogo: form.adminLogo.trim(),
    userHomeLogo: form.userHomeLogo.trim(),
    titleLogo: form.titleLogo.trim(),
    systemLogKeepDays: form.systemLogKeepDays,
    systemLogKeepCount: form.systemLogKeepCount,
    userLogKeepDays: form.userLogKeepDays,
    userLogKeepCount: form.userLogKeepCount
  }

  try {
    const response = await saveSystemSettings(payload)

    if (response.code !== 20000) {
      alert(response.message || '系统设置保存失败')
      return
    }

    settingsStore.saveSettings(payload)
    appendOperationLog({
      module: 'SYSTEM',
      action: 'UPDATE',
      target: `系统基础设置：${payload.systemName}`
    })

    alert('基础设置已保存')
  } catch (error) {
    alert('系统设置保存失败')
  }
}

onMounted(() => {
  void syncLogoPixelText()
})
</script>

<template>
  <div class="page-container">
    <el-card>
      <div class="action-row">
        <el-button type="primary" @click="handleSave">保存设置</el-button>
      </div>
    </el-card>

    <el-card>
      <div class="content-grid">
        <div class="basic-grid">
          <div class="basic-field">
            <div class="field-label">系统名称</div>
            <el-input v-model="form.systemName" placeholder="请输入系统名称" />
          </div>

          <div class="basic-field">
            <div class="field-label">系统域名</div>
            <el-input
              v-model="form.systemDomain"
              placeholder="例如：https://survey.example.com"
            />
          </div>

          <div class="basic-field page-size-field">
            <div class="field-label">默认分页条数</div>
            <el-select v-model="form.defaultPageSize" style="width: 100%;">
              <el-option label="10 条/页" :value="10" />
              <el-option label="20 条/页" :value="20" />
              <el-option label="50 条/页" :value="50" />
              <el-option label="100 条/页" :value="100" />
            </el-select>
          </div>
        </div>

        <section class="logo-section">
          <div class="field-label">Logo 设置</div>
          <div class="logo-grid">
            <article class="logo-card">
              <div class="logo-card-head">
                <div>
                  <div class="logo-title">网页标题 LOGO</div>
                  <div class="logo-desc">显示位置：浏览器标签页</div>
                </div>
                <el-tag type="warning" effect="plain">
                  推荐 {{ getLogoRecommendSize('titleLogo') }}
                </el-tag>
              </div>

              <input
                ref="titleLogoInputRef"
                class="hidden-file-input"
                type="file"
                accept="image/*"
                @change="(event) => handleLogoSelected(event, 'titleLogo')"
              >

              <div class="logo-dropzone" @click="triggerLogoUpload('titleLogo')">
                <img
                  v-if="form.titleLogo"
                  :src="form.titleLogo"
                  alt="网页标题 LOGO"
                  class="logo-preview-image"
                >
                <div v-else class="logo-empty">
                  <div class="logo-empty-title">点击上传网页标题 LOGO</div>
                  <div class="logo-empty-tip">支持 PNG/JPG/SVG</div>
                </div>
              </div>

              <div class="logo-meta-row">
                <span>当前尺寸：{{ logoPixelText.titleLogo }}</span>
                <span>最大：2MB</span>
              </div>

              <div class="logo-btn-row">
                <el-button size="small" type="primary" plain @click="triggerLogoUpload('titleLogo')">
                  选择图片
                </el-button>
                <el-button
                  v-if="form.titleLogo"
                  size="small"
                  @click="clearLogo('titleLogo')"
                >
                  清除
                </el-button>
              </div>
            </article>

            <article class="logo-card">
              <div class="logo-card-head">
                <div>
                  <div class="logo-title">管理后台 Logo</div>
                  <div class="logo-desc">显示位置：后台左侧导航头部</div>
                </div>
                <el-tag type="primary" effect="plain">
                  推荐 {{ getLogoRecommendSize('adminLogo') }}
                </el-tag>
              </div>

              <input
                ref="adminLogoInputRef"
                class="hidden-file-input"
                type="file"
                accept="image/*"
                @change="(event) => handleLogoSelected(event, 'adminLogo')"
              >

              <div class="logo-dropzone" @click="triggerLogoUpload('adminLogo')">
                <img
                  v-if="form.adminLogo"
                  :src="form.adminLogo"
                  alt="管理后台 Logo"
                  class="logo-preview-image"
                >
                <div v-else class="logo-empty">
                  <div class="logo-empty-title">点击上传管理后台 Logo</div>
                  <div class="logo-empty-tip">支持 PNG/JPG/SVG</div>
                </div>
              </div>

              <div class="logo-meta-row">
                <span>当前尺寸：{{ logoPixelText.adminLogo }}</span>
                <span>最大：2MB</span>
              </div>

              <div class="logo-btn-row">
                <el-button size="small" type="primary" plain @click="triggerLogoUpload('adminLogo')">
                  选择图片
                </el-button>
                <el-button
                  v-if="form.adminLogo"
                  size="small"
                  @click="clearLogo('adminLogo')"
                >
                  清除
                </el-button>
              </div>
            </article>

            <article class="logo-card">
              <div class="logo-card-head">
                <div>
                  <div class="logo-title">用户主页 Logo</div>
                  <div class="logo-desc">显示位置：工人个人首页顶部</div>
                </div>
                <el-tag type="success" effect="plain">
                  推荐 {{ getLogoRecommendSize('userHomeLogo') }}
                </el-tag>
              </div>

              <input
                ref="userHomeLogoInputRef"
                class="hidden-file-input"
                type="file"
                accept="image/*"
                @change="(event) => handleLogoSelected(event, 'userHomeLogo')"
              >

              <div class="logo-dropzone" @click="triggerLogoUpload('userHomeLogo')">
                <img
                  v-if="form.userHomeLogo"
                  :src="form.userHomeLogo"
                  alt="用户主页 Logo"
                  class="logo-preview-image"
                >
                <div v-else class="logo-empty">
                  <div class="logo-empty-title">点击上传用户主页 Logo</div>
                  <div class="logo-empty-tip">支持 PNG/JPG/SVG</div>
                </div>
              </div>

              <div class="logo-meta-row">
                <span>当前尺寸：{{ logoPixelText.userHomeLogo }}</span>
                <span>最大：2MB</span>
              </div>

              <div class="logo-btn-row">
                <el-button size="small" type="primary" plain @click="triggerLogoUpload('userHomeLogo')">
                  选择图片
                </el-button>
                <el-button
                  v-if="form.userHomeLogo"
                  size="small"
                  @click="clearLogo('userHomeLogo')"
                >
                  清除
                </el-button>
              </div>
            </article>
          </div>
        </section>

        <el-card>
          <div class="switch-row">
            <div>
              <div class="switch-title">启用日志记录</div>
              <div class="switch-desc">控制后台操作日志是否记录</div>
            </div>
            <el-switch v-model="form.enableLog" />
          </div>
        </el-card>

        <el-card>
          <div class="log-setting-title">日志保留策略</div>
          <div class="log-setting-grid">
            <div class="log-setting-item">
              <div class="log-setting-name">系统日志</div>
              <div class="log-setting-desc">系统管理员/业务管理员产生的日志</div>
              <div class="log-setting-row">
                <span class="log-setting-label">保留数量</span>
                <el-input-number v-model="form.systemLogKeepCount" :min="0" :max="999999" />
                <span class="log-setting-unit">条</span>
              </div>
              <div class="log-setting-row">
                <span class="log-setting-label">保留时间</span>
                <el-input-number v-model="form.systemLogKeepDays" :min="0" :max="3650" />
                <span class="log-setting-unit">天</span>
              </div>
            </div>

            <div class="log-setting-item">
              <div class="log-setting-name">用户日志</div>
              <div class="log-setting-desc">普通用户产生的日志</div>
              <div class="log-setting-row">
                <span class="log-setting-label">保留数量</span>
                <el-input-number v-model="form.userLogKeepCount" :min="0" :max="999999" />
                <span class="log-setting-unit">条</span>
              </div>
              <div class="log-setting-row">
                <span class="log-setting-label">保留时间</span>
                <el-input-number v-model="form.userLogKeepDays" :min="0" :max="3650" />
                <span class="log-setting-unit">天</span>
              </div>
            </div>
          </div>
          <div class="log-setting-hint">
            数量或时间设置为 0 表示不限制该维度。
          </div>
        </el-card>

        <el-card>
          <div class="switch-row">
            <div>
              <div class="switch-title">启用移动端断点续答</div>
              <div class="switch-desc">控制答题过程中是否自动保存草稿</div>
            </div>
            <el-switch v-model="form.enableResumeDraft" />
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.content-grid {
  display: grid;
  gap: 18px;
  width: 100%;
}

.basic-grid {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(280px, 1fr) minmax(180px, 0.7fr);
  gap: 16px;
  align-items: end;
}

.basic-field {
  display: grid;
  gap: 8px;
}

.page-size-field {
  max-width: 320px;
}

.field-label {
  font-weight: 600;
}

.logo-section {
  display: grid;
  gap: 10px;
}

.logo-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.logo-card {
  border: 1px solid #e4eaf3;
  border-radius: 12px;
  padding: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.logo-card-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.logo-title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
}

.logo-desc {
  color: #677284;
  font-size: 12px;
  margin-top: 5px;
}

.hidden-file-input {
  display: none;
}

.logo-dropzone {
  min-height: 84px;
  border: 1px dashed #b9c7dd;
  border-radius: 10px;
  background: #f6f9ff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  box-sizing: border-box;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logo-dropzone:hover {
  border-color: #4f8dff;
  background: #eff5ff;
}

.logo-preview-image {
  max-width: 100%;
  max-height: 52px;
  object-fit: contain;
}

.logo-empty {
  text-align: center;
}

.logo-empty-title {
  color: #385273;
  font-weight: 600;
  line-height: 1.4;
}

.logo-empty-tip {
  color: #8e9ab0;
  font-size: 12px;
  margin-top: 4px;
}

.logo-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
  color: #667283;
  font-size: 12px;
}

.logo-btn-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.switch-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.switch-desc {
  color: #666;
}

.log-setting-title {
  font-weight: 700;
  margin-bottom: 12px;
}

.log-setting-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.log-setting-item {
  border: 1px solid #e4eaf3;
  border-radius: 12px;
  padding: 14px;
  background: #fbfcff;
  display: grid;
  gap: 10px;
}

.log-setting-name {
  font-weight: 600;
}

.log-setting-desc {
  color: #7b8698;
  font-size: 12px;
}

.log-setting-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-setting-label {
  width: 72px;
  color: #4d5b70;
}

.log-setting-unit {
  color: #7b8698;
}

.log-setting-hint {
  margin-top: 12px;
  color: #8a95a8;
  font-size: 12px;
}

@media (max-width: 992px) {
  .basic-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .logo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .page-size-field {
    max-width: 100%;
  }

  .log-setting-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .basic-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .logo-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

