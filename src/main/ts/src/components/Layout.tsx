import { Link, useLocation } from 'react-router-dom'
import { Button } from './ui/button'
import { useTheme } from '@/lib/context/theme-provider'
import { Moon, Sun } from 'lucide-react'

const Navigation = () => {
  const { pathname } = useLocation()
  const { theme, setTheme } = useTheme()
  
  const isActive = (path: string) => pathname === path

  const navItems = [
    { path: '/', label: '首页' },
    { path: '/secretary_manager', label: '秘书管理' },
    { path: '/user_secretary_mapping', label: '用户秘书映射' },
    { path: '/template_manager', label: '模板管理' },

    { path: '/logs', label: '日志查看' },
  ]

  return (
    <header className="fixed top-0 left-0 right-0 h-14 border-b bg-background/95 backdrop-blur z-30">
      <div className="container flex items-center justify-between h-full">
        <div className="flex items-center gap-6">
          <Link to="/" className="font-bold text-xl">秘书管理系统</Link>
          <nav className="hidden md:flex items-center gap-6">
            {navItems.map(({ path, label }) => (
              <Link 
                key={path} 
                to={path}
                className={`text-sm font-medium transition-colors hover:text-primary ${
                  isActive(path) ? 'text-primary' : 'text-muted-foreground'
                }`}
              >
                {label}
              </Link>
            ))}
          </nav>
        </div>
        <div className="flex items-center">
          <Button 
            variant="ghost" 
            size="icon"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          >
            {theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
          </Button>
        </div>
      </div>
    </header>
  )
}

interface LayoutProps {
  children: React.ReactNode
}

const Layout = ({ children }: LayoutProps) => {
  return (
    <div className="min-h-screen flex flex-col">
      <Navigation />
      <main className="flex-1 pt-20 pb-10 container">
        {children}
      </main>
      <footer className="py-2 border-t bg-muted/50">
        <div className="container text-center text-sm text-muted-foreground">
          © {new Date().getFullYear()} 秘书管理系统
        </div>
      </footer>
    </div>
  )
}

export default Layout 