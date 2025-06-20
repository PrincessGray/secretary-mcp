import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { TaskTemplate, TemplateInfo, ConfigParam, ConfigParamCategory } from "@/types/models";
import { templateApi } from "@/services/api";
import { useToast } from "@/components/ui/use-toast";

interface CreateTaskDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: {
    secretaryId: string;
    templateId: string;
    name: string;
    customParams?: Record<string, any>;
  }) => Promise<boolean>;
  templates: TemplateInfo[];
  secretaryId: string;
  isLoading: boolean;
}

export function CreateTaskDialog({
  isOpen,
  onClose,
  onSubmit,
  templates,
  secretaryId,
  isLoading
}: CreateTaskDialogProps) {
  const { toast } = useToast();
  const [name, setName] = useState("");
  const [templateId, setTemplateId] = useState("");
  const [customParams, setCustomParams] = useState<Record<string, any>>({});
  const [selectedTemplate, setSelectedTemplate] = useState<TaskTemplate | null>(null);
  const [isLoadingTemplate, setIsLoadingTemplate] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 当选择模板时，获取完整的模板详情
  useEffect(() => {
    if (templateId) {
      setIsLoadingTemplate(true);
      
      // 使用fetchById获取完整模板
      templateApi.fetchById(templateId)
        .then(template => {
          console.log("获取到的完整模板数据:", template);
          setSelectedTemplate(template);
          
          // 设置默认参数值
          if (template && template.customizableParams && template.customizableParams.length > 0) {
            const defaultParams: Record<string, any> = {};
            template.customizableParams.forEach(param => {
              defaultParams[param.name] = param.defaultValue;
            });
            setCustomParams(defaultParams);
          } else {
            setCustomParams({});
          }
        })
        .catch(error => {
          console.error("获取模板详情失败", error);
          toast({
            title: "错误",
            description: "获取模板详情失败: " + error.message,
            variant: "destructive"
          });
          setSelectedTemplate(null);
          setCustomParams({});
        })
        .finally(() => {
          setIsLoadingTemplate(false);
        });
    } else {
      setSelectedTemplate(null);
      setCustomParams({});
    }
  }, [templateId, toast]);

  const handleSubmit = async () => {
    if (!name.trim() || !templateId || !secretaryId) return;
    
    setIsSubmitting(true);
    
    try {
      // 处理参数大小写问题
      const processedParams = processCustomParams(customParams, selectedTemplate);
      
      // 使用onSubmit回调，该回调在useTasks中已被修改为两步过程
      const success = await onSubmit({
        secretaryId,
        templateId,
        name,
        customParams: processedParams
      });
      
      if (success) {
        // 重置表单
        setName("");
        setTemplateId("");
        setCustomParams({});
        onClose();
      }
      
      return success;
    } catch (error: any) {
      console.error("创建任务失败:", error);
      toast({
        title: "错误",
        description: "创建任务失败: " + error.message,
        variant: "destructive"
      });
      return false;
    } finally {
      setIsSubmitting(false);
    }
  };

  // 处理参数大小写问题和其他转换
  const processCustomParams = (params: Record<string, any>, template: TaskTemplate | null): Record<string, any> => {
    const processedParams: Record<string, any> = { ...params };
    
    // 如果没有模板或参数定义，直接返回原始参数
    if (!template?.customizableParams) return processedParams;
    
    template.customizableParams.forEach(param => {
      // 如果参数不存在，跳过
      if (processedParams[param.name] === undefined) return;
      
      // 根据参数类型进行处理
      if (param.category === ConfigParamCategory.STDIO_ENV) {
        // 对于环境变量，可能需要处理大小写
        const upperCaseName = param.name.toUpperCase();
        
        // 如果参数名与大写版本不同，且模板中使用了大写环境变量
        const envVars = template.connectionProfile?.stdioConfig?.environmentVars || {};
        if (param.name !== upperCaseName && envVars[upperCaseName] !== undefined) {
          processedParams[upperCaseName] = processedParams[param.name];
          // 保留原始小写键，以确保兼容性
        }
      } else if (param.category === ConfigParamCategory.STDIO_ARG) {
        // 确保布尔参数是实际的布尔值
        if (typeof processedParams[param.name] === 'string') {
          processedParams[param.name] = processedParams[param.name] === 'true' || 
                                     processedParams[param.name] === '1' || 
                                     processedParams[param.name] === 'yes';
        }
      }
    });
    
    return processedParams;
  };

  // 更新参数值
  const handleParamChange = (name: string, value: any) => {
    setCustomParams(prev => ({
      ...prev,
      [name]: value
    }));
  };

  // 渲染参数表单
  const renderParamFields = () => {
    if (isLoadingTemplate) {
      return <div className="text-sm text-muted-foreground">加载模板详情中...</div>;
    }
    
    if (!selectedTemplate || !selectedTemplate.customizableParams || selectedTemplate.customizableParams.length === 0) {
      return <div className="text-sm text-muted-foreground">此模板没有可定制参数</div>;
    }

    return (
      <div className="space-y-4">
        {selectedTemplate.customizableParams.map(param => (
          <div key={param.name} className="grid gap-2">
            {param.type === "boolean" || param.category === ConfigParamCategory.STDIO_ARG ? (
              <div className="flex items-center justify-between">
                <div>
                  <Label htmlFor={param.name}>{param.displayName || param.name}</Label>
                  {param.description && (
                    <p className="text-xs text-muted-foreground">{param.description}</p>
                  )}
                </div>
                <Switch
                  id={param.name}
                  checked={!!customParams[param.name]}
                  onCheckedChange={(checked) => handleParamChange(param.name, checked)}
                />
              </div>
            ) : (
              <div>
                <Label htmlFor={param.name}>{param.displayName || param.name}</Label>
                {param.description && (
                  <p className="text-xs text-muted-foreground">{param.description}</p>
                )}
                <Input
                  id={param.name}
                  value={customParams[param.name] || ""}
                  onChange={(e) => handleParamChange(param.name, e.target.value)}
                  className="mt-1"
                  disabled={isLoading || isSubmitting}
                  required={param.required}
                />
              </div>
            )}
          </div>
        ))}
      </div>
    );
  };

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[550px]">
        <DialogHeader>
          <DialogTitle>创建新任务</DialogTitle>
        </DialogHeader>
        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="name">任务名称</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="输入任务名称"
              disabled={isLoading || isSubmitting}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="template">选择模板</Label>
            <Select
              value={templateId}
              onValueChange={setTemplateId}
              disabled={isLoading || isSubmitting}
            >
              <SelectTrigger>
                <SelectValue placeholder="选择模板" />
              </SelectTrigger>
              <SelectContent>
                {templates.map(template => (
                  <SelectItem key={template.id} value={template.id}>
                    {template.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          
          {templateId && (
            <div className="grid gap-3">
              <Label>自定义参数</Label>
              <div className="border rounded-md p-3 bg-muted/10">
                {renderParamFields()}
              </div>
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isLoading || isSubmitting}>
            取消
          </Button>
          <Button 
            onClick={handleSubmit} 
            disabled={isLoading || isSubmitting || !name.trim() || !templateId}
          >
            {isLoading || isSubmitting ? "创建中..." : "创建"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
