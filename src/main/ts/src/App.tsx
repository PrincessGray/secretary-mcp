import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import { ThemeProvider } from '@/lib/context/theme-provider'
import { Toaster } from '@/components/ui/toaster'

// 导入页面组件
import Layout from '@/components/Layout'
import Home from '@/components/features/Home'
import SecretaryManager from '@/components/features/SecretaryManager'
import TemplateManager from '@/components/features/TemplateManager'
import LogViewer from '@/components/features/LogViewer'
import UserSecretaryMapping from '@/components/features/UserSecretaryMapping'

function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="secretary-theme">
      <Router>
        <Layout>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/secretary_manager" element={<SecretaryManager />} />
            <Route path="/template_manager" element={<TemplateManager />} />
            <Route path="/logs" element={<LogViewer />} />
            <Route path="/user_secretary_mapping" element={<UserSecretaryMapping />} />
          </Routes>
        </Layout>
        <Toaster />
      </Router>
    </ThemeProvider>
  )
}

export default App 