import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import { i18n } from '@mlightcad/cad-viewer'
import App from './App.vue'
import './styles.css'

createApp(App).use(i18n).use(ElementPlus).mount('#app')
