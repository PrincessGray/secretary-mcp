import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { Info, PlusCircle } from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import { TemplateDetailDialog } from "./TemplateDetailDialog";
import { TaskTemplate } from "@/types/models";

// 预定义模板类型
interface MarketplaceTemplate {
  id: string;
  name: string;
  description: string;
  connectionType: string;
  category: string;
  tags: string[];
  author: string;
  downloadCount?: number;
  version?: string;
  templateData: any; // 完整的模板数据，用于创建
}

interface TemplateMarketplaceProps {
  onInstall: (templateData: any) => Promise<boolean>;
  isLoading?: boolean;
}

export function TemplateMarketplace({ onInstall, isLoading = false }: TemplateMarketplaceProps) {
  const { toast } = useToast();
  const [installingId, setInstallingId] = useState<string | null>(null);
  const [marketplaceTemplates, setMarketplaceTemplates] = useState<MarketplaceTemplate[]>([]);
  const [marketplaceLoading, setMarketplaceLoading] = useState(false);
  
  // 新增状态管理模板详情对话框
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<TaskTemplate | null>(null);
  
  // 打开模板详情对话框
  const openTemplateDetail = (template: MarketplaceTemplate) => {
    // 将市场模板数据转换为TaskTemplate格式
    const taskTemplate: TaskTemplate = {
      id: template.id,
      name: template.name,
      description: template.description,
      createdAt: new Date().toISOString(), // 市场模板没有创建时间，使用当前时间
      updatedAt: new Date().toISOString(),
      
      // 构造connectionProfile
      connectionProfile: {
        connectionType: template.connectionType.toUpperCase() as any,
        connectionTimeoutSeconds: 120,
        
        // 根据连接类型设置特定配置
        stdioConfig: template.connectionType.toLowerCase() === 'stdio' ? {
          command: template.templateData.stdioCommand,
          commandArgs: template.templateData.stdioArgs || [],
          environmentVars: template.templateData.stdioEnv || {},
          managementType: template.templateData.managementType
        } : undefined,
        
        sseConfig: template.connectionType.toLowerCase() === 'sse' ? {
          serverUrl: template.templateData.sseServerUrl,
          bearerToken: template.templateData.sseAuthToken
        } : undefined,
        
        // 通用配置
        generalConfig: {
          enableRoots: template.templateData.enableRoots,
          enableSampling: template.templateData.enableSampling,
          timeoutSeconds: template.templateData.timeoutSeconds
        }
      },
      
      // 复制自定义参数
      customizableParams: template.templateData.customizableParams || [],
      
      // 其他数据
      defaultConfig: template.templateData.defaultConfig || {},
      metadata: {
        author: template.author,
        category: template.category,
        tags: template.tags,
        version: template.version,
        downloadCount: template.downloadCount
      }
    };
    
    setSelectedTemplate(taskTemplate);
    setDetailDialogOpen(true);
  };
  
  // 加载预定义模板
  const loadMarketplaceTemplates = async () => {
    setMarketplaceLoading(true);
    
    try {
      // 尝试从resources加载模板
      const response = await fetch('/api/templates/marketplace');
      
      if (response.ok) {
        const data = await response.json();
        setMarketplaceTemplates(data);
      } else {
        // 如果API不存在，使用示例数据
        setMarketplaceTemplates(sampleTemplates);
        console.warn('使用示例模板数据，未能从后端加载预定义模板');
      }
    } catch (error) {
      console.error('加载模板市场数据失败:', error);
      // 如果出错，使用示例数据
      setMarketplaceTemplates(sampleTemplates);
      toast({
        title: "无法连接到模板市场",
        description: "已加载示例模板供预览",
        variant: "destructive"
      });
    } finally {
      setMarketplaceLoading(false);
    }
  };
  
  // 安装模板
  const handleInstall = async (template: MarketplaceTemplate) => {
    setInstallingId(template.id);
    
    try {
      // 创建一个新对象，确保使用正确的名称
      const templateDataWithCorrectName = {
        ...template.templateData,
        name: template.name  // 使用模板市场中显示的名称
      };
      
      const success = await onInstall(templateDataWithCorrectName);
      if (success) {
        toast({
          title: "模板安装成功",
          description: `${template.name} 已添加到您的模板列表`,
        });
      }
    } catch (error) {
      console.error('安装模板失败:', error);
      toast({
        title: "模板安装失败",
        description: "无法安装所选模板，请稍后重试",
        variant: "destructive"
      });
    } finally {
      setInstallingId(null);
    }
  };
  
  // 如果没有加载过模板，自动加载
  useEffect(() => {
    if (marketplaceTemplates.length === 0 && !marketplaceLoading) {
      loadMarketplaceTemplates();
    }
  }, [marketplaceTemplates.length, marketplaceLoading]);
  
  if (marketplaceLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-12">
        <Spinner size="lg" />
        <div className="mt-4 text-muted-foreground">加载模板市场数据...</div>
      </div>
    );
  }
  
  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-bold">模板市场</h2>
        <Button 
          variant="outline" 
          size="sm" 
          onClick={loadMarketplaceTemplates}
          disabled={marketplaceLoading}
        >
          刷新市场
        </Button>
      </div>
      
      <p className="text-muted-foreground">
        从模板市场获取预定义的模板，快速开始你的项目。
      </p>
      
      {marketplaceTemplates.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          暂无可用的市场模板
        </div>
      ) : (
        <div className="space-y-4">
          {marketplaceTemplates.map((template) => (
            <Card key={template.id} className="overflow-hidden">
              <CardContent className="p-6">
                <div className="flex flex-col space-y-3">
                  {/* 第一行：标题、徽章和按钮 */}
                  <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold flex-shrink-0 mr-4">{template.name}</h3>
                    
                    <div className="flex items-center gap-4 flex-grow justify-start">
                      <Badge>{template.connectionType}</Badge>
                      
                      {template.connectionType?.toLowerCase() === 'stdio' && 
                        template.templateData?.managementType && (
                          <Badge>{template.templateData.managementType}</Badge>
                      )}
                    </div>
                    
                    <div className="flex space-x-2 flex-shrink-0">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => openTemplateDetail(template)}
                      >
                        <Info className="mr-1 h-4 w-4" />
                        详情
                      </Button>
                      <Button
                        size="sm"
                        onClick={() => handleInstall(template)}
                        disabled={installingId === template.id || isLoading}
                      >
                        {installingId === template.id ? (
                          <>
                            <Spinner className="mr-1 h-4 w-4" />
                            安装中
                          </>
                        ) : (
                          <>
                            <PlusCircle className="mr-1 h-4 w-4" />
                            安装
                          </>
                        )}
                      </Button>
                    </div>
                  </div>
                  
                  {/* 第二行：描述 */}
                  <div className="flex flex-col space-y-2">
                    <p className="text-sm text-muted-foreground">
                      {template.description || "无描述"}
                    </p>
                    
                    <div className="flex flex-wrap gap-2 items-center">
                      {template.tags.map((tag, index) => (
                        <Badge key={index} variant="outline" className="text-xs">{tag}</Badge>
                      ))}
                      {template.version && (
                        <span className="text-xs text-muted-foreground flex items-center">版本: {template.version}</span>
                      )}
                      {template.author && (
                        <span className="text-xs text-muted-foreground flex items-center">作者: {template.author}</span>
                      )}
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
      
      {/* 模板详情对话框 */}
      <TemplateDetailDialog
        template={selectedTemplate}
        isOpen={detailDialogOpen}
        onClose={() => setDetailDialogOpen(false)}
      />
    </div>
  );
}

// 示例模板数据
const sampleTemplates: MarketplaceTemplate[] = [
  {
    id: "template-edgeone-pages",
    name: "EdgeOne Pages",
    description: "将HTML内容部署到EdgeOne Pages并生成访问链接的模板",
    connectionType: "STDIO",
    category: "部署工具",
    tags: ["部署", "静态网站", "EdgeOne"],
    author: "SecretaryMCP Team",
    downloadCount: 156,
    version: "1.0.0",
    templateData: {
      name: "edgeone-pages",
      description: "可以将HTML内容部署到EdgeOne Pages并生成访问链接",
      connectionType: "stdio",
      stdioCommand: "npx",
      managementType: "NPX",
      stdioArgs: ["edgeone-pages-mcp"],
      stdioEnv: {
        "WORKING_DIR": "default"
      },
      enableRoots: false,
      enableSampling: false,
      customizableParams: []
    }
  },
  {
    id: "template-midjourney-prompt",
    name: "Midjourney",
    description: "生成并优化Midjourney提示词的模板, 支持中英文输入",
    connectionType: "STDIO",
    category: "AI工具",
    tags: ["AI", "图像生成", "Midjourney"],
    author: "SecretaryMCP Team",
    downloadCount: 328,
    version: "1.1.2",
    templateData: {
      name: "midjourney-prompt",
      description: "生成并优化Midjourney提示词的模板, 支持中英文输入",
      connectionType: "stdio",
      stdioCommand: "npx",
      managementType: "NPX",
      stdioArgs: ["midjourney-prompt-builder"],
      stdioEnv: {
        "WORKING_DIR": "default"
      },
      enableRoots: false,
      enableSampling: false,
      customizableParams: [
        {
          name: "LANGUAGE",
          displayName: "语言偏好",
          description: "设置生成提示词的语言，支持中文或英文",
          type: "string",
          required: false,
          defaultValue: "zh-CN",
          category: "STDIO_ENV"
        }
      ]
    }
  },
  {
    id: "template-code-reviewer",
    name: "代码审查助手",
    description: "自动审查代码并提供改进建议的模板",
    connectionType: "STDIO",
    category: "开发工具",
    tags: ["代码", "审查", "开发"],
    author: "SecretaryMCP Team",
    version: "0.9.5",
    templateData: {
      name: "code-reviewer",
      description: "自动审查代码并提供改进建议",
      connectionType: "stdio",
      stdioCommand: "npx",
      managementType: "NPX",
      stdioArgs: ["code-review-assistant"],
      stdioEnv: {
        "WORKING_DIR": "default",
        "DETAIL_LEVEL": "medium"
      },
      enableRoots: false,
      enableSampling: false,
      customizableParams: [
        {
          name: "detailLevel",
          displayName: "审查详细程度",
          description: "设置代码审查的详细程度: low, medium, high",
          type: "string",
          required: false,
          defaultValue: "medium",
          category: "STDIO_ENV"
        },
        {
          name: "language",
          displayName: "代码语言",
          description: "指定代码的主要语言，如javascript, python等",
          type: "string",
          required: false,
          defaultValue: "",
          category: "STDIO_ENV"
        }
      ]
    }
  }
];
