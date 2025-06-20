import { Button } from "@/components/ui/button"
import { ArrowRight, Settings, FileText, Users, ExternalLink, ChevronRight } from "lucide-react"
import { Link } from "react-router-dom"

export default function Home() {
  return (
    <div className="min-h-[calc(100vh-4rem)] flex flex-col">
      {/* 顶部英雄区域 */}
      <div className="relative overflow-hidden bg-gradient-to-b from-blue-50 via-white to-white dark:from-gray-900 dark:via-gray-900 dark:to-gray-900 pb-8">
        <div className="absolute inset-0 bg-grid-slate-100 [mask-image:linear-gradient(0deg,white,transparent)] dark:[mask-image:linear-gradient(0deg,rgba(17,24,39,1),transparent)]"></div>
        <div className="absolute right-0 top-0 -z-10 h-[300px] w-[300px] rounded-full bg-blue-500/20 blur-3xl dark:bg-blue-900/20"></div>
        <div className="absolute left-10 top-40 -z-10 h-[200px] w-[200px] rounded-full bg-purple-500/20 blur-3xl dark:bg-purple-900/20"></div>
        
        <div className="container mx-auto flex flex-col items-center justify-center px-4 py-16 text-center">
          <div className="animate-fadeInUp">
            <h1 className="text-5xl font-bold tracking-tight sm:text-6xl mb-6 bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent dark:from-blue-400 dark:to-purple-400">
              秘书管理系统
            </h1>
            <p className="max-w-[600px] text-muted-foreground mb-8 text-lg">
              一个现代化的管理系统，帮助您高效管理数字助手、模板和用户映射关系。
            </p>
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Link to="/secretary_manager">
                <Button size="lg" className="gap-2 shadow-lg hover:shadow-blue-200 transition-all duration-300 hover:-translate-y-1 dark:hover:shadow-blue-900/30">
                  开始使用
                  <ArrowRight className="h-4 w-4" />
                </Button>
              </Link>
              <Link to="/user_secretary_mapping">
                <Button variant="outline" size="lg" className="gap-2 shadow hover:shadow-md transition-all duration-300 hover:-translate-y-1">
                  用户映射
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </div>
      
      {/* 特性展示区域 */}
      <div className="container mx-auto px-4 py-8 bg-white dark:bg-gray-900">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="group bg-card rounded-xl border p-8 hover:shadow-lg transition-all duration-300 hover:-translate-y-1 relative overflow-hidden dark:border-gray-800 dark:bg-gray-800/50">
            <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-500 to-blue-300 transform origin-left scale-x-0 group-hover:scale-x-100 transition-transform duration-300"></div>
            <div className="rounded-full bg-blue-100 dark:bg-blue-900/30 w-12 h-12 flex items-center justify-center mb-4">
              <Settings className="text-blue-600 dark:text-blue-400 h-6 w-6" />
            </div>
            <h3 className="font-semibold text-xl mb-3">秘书管理</h3>
            <p className="text-muted-foreground mb-6">
              轻松添加、编辑和删除数字秘书，管理它们的属性和任务设置，实现精确的控制与自动化。
            </p>
            <Link to="/secretary_manager" className="inline-flex items-center text-blue-600 hover:text-blue-700 font-medium dark:text-blue-400 dark:hover:text-blue-300">
              前往管理
              <ExternalLink className="ml-1 h-4 w-4" />
            </Link>
          </div>
          
          <div className="group bg-card rounded-xl border p-8 hover:shadow-lg transition-all duration-300 hover:-translate-y-1 relative overflow-hidden dark:border-gray-800 dark:bg-gray-800/50">
            <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-purple-500 to-purple-300 transform origin-left scale-x-0 group-hover:scale-x-100 transition-transform duration-300"></div>
            <div className="rounded-full bg-purple-100 dark:bg-purple-900/30 w-12 h-12 flex items-center justify-center mb-4">
              <FileText className="text-purple-600 dark:text-purple-400 h-6 w-6" />
            </div>
            <h3 className="font-semibold text-xl mb-3">模板管理</h3>
            <p className="text-muted-foreground mb-6">
              创建和管理提示模板，以便快速部署数字秘书，确保工作流程标准化并提升效率。
            </p>
            <Link to="/template_manager" className="inline-flex items-center text-purple-600 hover:text-purple-700 font-medium dark:text-purple-400 dark:hover:text-purple-300">
              前往管理
              <ExternalLink className="ml-1 h-4 w-4" />
            </Link>
          </div>
          
          <div className="group bg-card rounded-xl border p-8 hover:shadow-lg transition-all duration-300 hover:-translate-y-1 relative overflow-hidden dark:border-gray-800 dark:bg-gray-800/50">
            <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-indigo-500 to-indigo-300 transform origin-left scale-x-0 group-hover:scale-x-100 transition-transform duration-300"></div>
            <div className="rounded-full bg-indigo-100 dark:bg-indigo-900/30 w-12 h-12 flex items-center justify-center mb-4">
              <Users className="text-indigo-600 dark:text-indigo-400 h-6 w-6" />
            </div>
            <h3 className="font-semibold text-xl mb-3">用户映射</h3>
            <p className="text-muted-foreground mb-6">
              精确管理用户与秘书之间的映射关系，分配不同的访问权限，确保系统安全性和用户体验。
            </p>
            <Link to="/user_secretary_mapping" className="inline-flex items-center text-indigo-600 hover:text-indigo-700 font-medium dark:text-indigo-400 dark:hover:text-indigo-300">
              前往管理
              <ExternalLink className="ml-1 h-4 w-4" />
            </Link>
          </div>
        </div>
      </div>

      {/* 注入全局CSS */}
      <style dangerouslySetInnerHTML={{
        __html: `
          @keyframes fadeInUp {
            from {
              opacity: 0;
              transform: translateY(20px);
            }
            to {
              opacity: 1;
              transform: translateY(0);
            }
          }
          .animate-fadeInUp {
            animation: fadeInUp 0.5s ease-out forwards;
          }
          .bg-grid-slate-100 {
            background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32' width='32' height='32' fill='none' stroke='rgb(226 232 240 / 0.2)'%3e%3cpath d='M0 .5H31.5V32'/%3e%3c/svg%3e");
          }
        `
      }} />
    </div>
  )
} 