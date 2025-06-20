import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { TaskTemplate } from "@/types/models";

interface TemplateDetailDialogProps {
  template: TaskTemplate | null;
  isOpen: boolean;
  onClose: () => void;
  onEdit?: (templateId: string) => void;
}

export function TemplateDetailDialog({ 
  template, 
  isOpen, 
  onClose, 
  onEdit 
}: TemplateDetailDialogProps) {
  // 如果没有模板数据，不渲染对话框内容
  if (!template) {
    return null;
  }

  // 格式化显示简单对象
  const formatObject = (obj: Record<string, any>) => {
    return (
      <div className="border rounded bg-muted/10 p-3">
        <pre className="text-xs overflow-auto max-h-[300px]">
          {JSON.stringify(obj, null, 2)}
        </pre>
      </div>
    );
  };

  // 格式化日期
  const formatDate = (dateString?: string) => {
    if (!dateString) return '未知';
    try {
      return new Date(dateString).toLocaleString();
    } catch {
      return dateString;
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[700px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center justify-between">
            <span>模板详情: {template.name}</span>
            {onEdit && (
              <Button variant="outline" size="sm" onClick={() => onEdit(template.id)}>
                编辑
              </Button>
            )}
          </DialogTitle>
        </DialogHeader>

        <Tabs defaultValue="basic">
          <TabsList className="grid grid-cols-4 mb-4">
            <TabsTrigger value="basic">基本信息</TabsTrigger>
            <TabsTrigger value="connection">连接配置</TabsTrigger>
            <TabsTrigger value="params">自定义参数</TabsTrigger>
            <TabsTrigger value="advanced">高级配置</TabsTrigger>
          </TabsList>

          <TabsContent value="basic" className="space-y-4">
            <div className="grid grid-cols-2 gap-2">
              <div>
                <h4 className="text-sm font-medium">模板ID:</h4>
                <p className="text-sm text-muted-foreground break-all">{template.id}</p>
              </div>
              <div>
                <h4 className="text-sm font-medium">名称:</h4>
                <p className="text-sm text-muted-foreground">{template.name}</p>
              </div>
              <div>
                <h4 className="text-sm font-medium">连接类型:</h4>
                <p className="text-sm text-muted-foreground">
                  {template.connectionProfile?.connectionType || "未知类型"}
                </p>
              </div>
              <div>
                <h4 className="text-sm font-medium">创建时间:</h4>
                <p className="text-sm text-muted-foreground">{formatDate(template.createdAt)}</p>
              </div>
              <div>
                <h4 className="text-sm font-medium">更新时间:</h4>
                <p className="text-sm text-muted-foreground">{formatDate(template.updatedAt)}</p>
              </div>
              <div className="col-span-2">
                <h4 className="text-sm font-medium">描述:</h4>
                <p className="text-sm text-muted-foreground">{template.description || "无描述"}</p>
              </div>
            </div>
          </TabsContent>

          <TabsContent value="connection" className="space-y-4">
            <div>
              <h4 className="text-sm font-medium mb-2">连接类型:</h4>
              <p className="text-sm mb-4">{template.connectionProfile?.connectionType || "未知"}</p>
              
              {template.connectionProfile?.connectionType === "STDIO" && template.connectionProfile.stdioConfig && (
                <>
                  <h4 className="text-sm font-medium mb-2">STDIO配置:</h4>
                  {formatObject(template.connectionProfile.stdioConfig)}
                </>
              )}
              
              {template.connectionProfile?.connectionType === "SSE" && template.connectionProfile.sseConfig && (
                <>
                  <h4 className="text-sm font-medium mb-2">SSE配置:</h4>
                  {formatObject(template.connectionProfile.sseConfig)}
                </>
              )}
            </div>
          </TabsContent>

          <TabsContent value="params" className="space-y-4">
            {template.customizableParams && template.customizableParams.length > 0 ? (
              <div className="space-y-4">
                {template.customizableParams.map((param, index) => (
                  <div key={index} className="border rounded-md p-4">
                    <div className="flex justify-between mb-2">
                      <div className="font-medium">{param.displayName}</div>
                      <Badge>{param.category}</Badge>
                    </div>
                    <div className="text-sm text-muted-foreground mb-2">{param.description}</div>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div>名称: <span className="font-medium">{param.name}</span></div>
                      <div>类型: <span className="font-medium">{param.type}</span></div>
                      <div>必填: <span className="font-medium">{param.required ? "是" : "否"}</span></div>
                      <div>默认值: <span className="font-medium">{param.defaultValue !== undefined ? String(param.defaultValue) : "无"}</span></div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">该模板没有自定义参数</p>
            )}
          </TabsContent>

          <TabsContent value="advanced" className="space-y-4">
            {template.connectionProfile?.generalConfig ? (
              <>
                <h4 className="text-sm font-medium mb-2">通用配置:</h4>
                {formatObject(template.connectionProfile.generalConfig)}
              </>
            ) : (
              <p className="text-sm text-muted-foreground">无高级配置</p>
            )}
          </TabsContent>
        </Tabs>
        
        <DialogFooter>
          <Button onClick={onClose}>
            关闭
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
} 