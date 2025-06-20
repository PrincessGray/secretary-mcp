import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import {
  Tabs,
  TabsList,
  TabsTrigger
} from "@/components/ui/tabs"
import { RefreshCw, PlusCircle, Search } from "lucide-react"

import { Input } from "@/components/ui/input"
import { TemplateList } from "./TemplateList"
import { CreateTemplateDialog } from "./CreateTemplateDialog"
import { useTemplates } from "./hooks/useTemplates"
import { TemplateDetailDialog } from "./TemplateDetailDialog"
import { TemplateMarketplace } from "./TemplateMarketplace"
import { Scrollable } from "@/components/ui/scrollable"

// 定义类型
interface Template {
  id: string
  name: string
  description: string
  connectionType: string
  connectionProfile: {
    connectionType: string
    stdioConfig?: {
      command: string
      commandArgs?: string[]
      environmentVars?: Record<string, string>
      managementType?: string
    }
    sseConfig?: {
      serverUrl: string
      bearerToken?: string
      headers?: Record<string, string>
    }
    generalConfig?: {
      timeoutSeconds?: number
      enableRoots?: boolean
      enableSampling?: boolean
    }
  }
  customizableParams: CustomParam[]
  createdAt: string
  updatedAt: string
  defaultConfig?: {
    enableRoots?: boolean;
    enableSampling?: boolean;
    // 其他默认配置属性
  }
}

interface CustomParam {
  name: string
  displayName: string
  description: string
  type: string
  required: boolean
  defaultValue: any
  category: string
}

interface TemplateListProps {
  templates: Template[];
  onView: (id: string) => void;
  onDelete: (id: string) => void;
}

export default function TemplateManager() {
  const   [searchQuery, setSearchQuery] = useState("")
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null)
  const [isDetailDialogOpen, setIsDetailDialogOpen] = useState(false)
  
  // 使用templates钩子
  const {
    templates,
    isLoading,
    apiResponse,
    requestLog,
    loadTemplates,
    getTemplateById,
    createTemplate,
    deleteTemplate
  } = useTemplates()
  
  // 处理模板删除
  const handleDeleteTemplate = (id: string) => {
    if (window.confirm("确定要删除此模板吗？此操作不可恢复。")) {
      deleteTemplate(id)
    }
  }
  
  // 处理模板创建
  const handleCreateTemplate = async (formData: any) => {
    const success = await createTemplate(formData)
    if (success) {
      setCreateDialogOpen(false)
    }
    return success
  }
  
  // 处理模板查看
  const handleViewTemplate = async (id: string) => {
    const template = await getTemplateById(id)
    if (template) {
      setSelectedTemplate(template as any)
      setIsDetailDialogOpen(true)
    }
  }
  
  // 关闭详情
  const handleCloseDetail = () => {
    setIsDetailDialogOpen(false)
    setSelectedTemplate(null)
  }
  
  // 过滤模板
  const filteredTemplates = templates.filter(template => 
    template.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    template.description?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    template.connectionType?.toLowerCase().includes(searchQuery.toLowerCase())
  )
  
  // 在组件中添加useEffect
  useEffect(() => {
    const scrollables = document.querySelectorAll('.scrollbar-reserved');
    
    scrollables.forEach(element => {
      let scrollTimeout: number | null = null;
      
      element.addEventListener('scroll', () => {
        element.classList.add('scrolling');
        
        // 清除之前的定时器
        if (scrollTimeout) {
          clearTimeout(scrollTimeout);
        }
        
        // 设置新的定时器，滚动停止后500ms隐藏滚动条
        scrollTimeout = window.setTimeout(() => {
          element.classList.remove('scrolling');
        }, 500);
      });
    });
    
    return () => {
      scrollables.forEach(element => {
        element.removeEventListener('scroll', () => {});
      });
    };
  }, []);
  
  return (
    <>
      <div className="container mx-auto py-6">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold">模板管理</h1>
          
          <div className="flex gap-2">
            <Button 
              variant="outline"
              size="sm"
              onClick={loadTemplates}
              disabled={isLoading}
              className="gap-2"
            >
              <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
              刷新数据
            </Button>
            
            <Button 
              className="gap-2"
              onClick={() => setCreateDialogOpen(true)}
              disabled={isLoading}
            >
              <PlusCircle className="h-4 w-4" />
              新建模板
            </Button>
          </div>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* 左侧: 模板列表 - 使用Scrollable组件 */}
          <div className="space-y-4">
            <div className="relative">
              <div className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none">
                <Search className="h-4 w-4 text-muted-foreground" />
              </div>
              <Input
                type="text"
                placeholder="搜索模板名称、描述或连接类型..."
                className="w-full pl-10 py-2"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            
            <Scrollable className="h-[calc(100vh-230px)] pr-2">
              <TemplateList 
                templates={filteredTemplates as any} 
                onView={handleViewTemplate} 
                onDelete={handleDeleteTemplate} 
              />
            </Scrollable>
          </div>
          
          {/* 右侧: 模板市场 - 使用Scrollable组件 */}
          <div className="bg-card rounded-lg border h-[calc(100vh-180px)]">
            <Scrollable className="h-full p-6">
              <TemplateMarketplace 
                onInstall={handleCreateTemplate}
                isLoading={isLoading} 
              />
            </Scrollable>
          </div>
        </div>
        
        {/* 创建模板对话框 */}
        <CreateTemplateDialog
          isOpen={createDialogOpen}
          onClose={() => setCreateDialogOpen(false)}
          onSubmit={handleCreateTemplate}
          isLoading={isLoading}
        />
        
        {/* 添加详情对话框 */}
        <TemplateDetailDialog 
          template={selectedTemplate as any} 
          isOpen={isDetailDialogOpen}
          onClose={() => setIsDetailDialogOpen(false)}
        />
      </div>
    </>
  )
} 