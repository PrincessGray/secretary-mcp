import { RemoteTask, TemplateInfo, ConnectionType } from "@/types/models";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

interface TaskDetailDialogProps {
  isOpen: boolean;
  onClose: () => void;
  task: RemoteTask | null;
  templates: TemplateInfo[];
}

export function TaskDetailDialog({
  isOpen,
  onClose,
  task,
  templates
}: TaskDetailDialogProps) {
  if (!task) return null;
  
  const getTemplateName = (templateId: string) => {
    const template = templates.find(t => t.id === templateId);
    return template ? template.name : templateId;
  };

  const getTemplateDescription = (templateId: string) => {
    const template = templates.find(t => t.id === templateId);
    return template?.description || '无描述';
  };

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
  
  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[700px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>任务详情: {task.name}</DialogTitle>
        </DialogHeader>
        
        <Tabs defaultValue="basic">
          <TabsList className="grid grid-cols-4 mb-4">
            <TabsTrigger value="basic">基本信息</TabsTrigger>
            <TabsTrigger value="connection">连接配置</TabsTrigger>
            <TabsTrigger value="params">可定制参数</TabsTrigger>
            <TabsTrigger value="metadata">元数据</TabsTrigger>
          </TabsList>
          
          <TabsContent value="basic" className="space-y-4">
            <div className="grid grid-cols-2 gap-2">
              <div>
                <h4 className="text-sm font-medium">任务ID:</h4>
                <p className="text-sm text-muted-foreground break-all">{task.id}</p>
              </div>
              <div>
                <h4 className="text-sm font-medium">任务名称:</h4>
                <p className="text-sm text-muted-foreground">{task.name}</p>
              </div>
              <div>
                <h4 className="text-sm font-medium">秘书名称:</h4>
                <p className="text-sm text-muted-foreground">{task.secretaryName}</p>
              </div>
              <div>
                <h4 className="text-sm font-medium">模板:</h4>
                <p className="text-sm text-muted-foreground">
                  {getTemplateName(task.templateId)}
                </p>
              </div>
              <div>
                <h4 className="text-sm font-medium">状态:</h4>
                <p className={`text-sm font-medium ${
                  task.status === 'ACTIVE' ? 'text-green-600' : 'text-orange-600'
                }`}>
                  {task.status === 'ACTIVE' ? '已激活' : '未激活'}
                </p>
              </div>
              <div>
                <h4 className="text-sm font-medium">创建时间:</h4>
                <p className="text-sm text-muted-foreground">
                  {new Date(task.createdAt).toLocaleString()}
                </p>
              </div>
              <div className="col-span-2">
                <h4 className="text-sm font-medium">模板描述:</h4>
                <p className="text-sm text-muted-foreground">
                  {getTemplateDescription(task.templateId)}
                </p>
              </div>
            </div>
          </TabsContent>
          
          <TabsContent value="connection" className="space-y-4">
            <div>
              <h4 className="text-sm font-medium mb-2">连接类型:</h4>
              <p className="text-sm mb-4">{task.connectionProfile.connectionType}</p>
              
              {task.connectionProfile.connectionType === ConnectionType.STDIO && task.connectionProfile.stdioConfig && (
                <>
                  <h4 className="text-sm font-medium mb-2">STDIO配置:</h4>
                  {formatObject(task.connectionProfile.stdioConfig)}
                </>
              )}
              
              {task.connectionProfile.connectionType === ConnectionType.SSE && task.connectionProfile.sseConfig && (
                <>
                  <h4 className="text-sm font-medium mb-2">SSE配置:</h4>
                  {formatObject(task.connectionProfile.sseConfig)}
                </>
              )}
              
              {task.connectionProfile.generalConfig && (
                <>
                  <h4 className="text-sm font-medium my-2">通用配置:</h4>
                  {formatObject(task.connectionProfile.generalConfig)}
                </>
              )}
            </div>
          </TabsContent>
          
          <TabsContent value="params" className="space-y-4">
            {task.customizableParams && task.customizableParams.length > 0 ? (
              formatObject(task.customizableParams)
            ) : (
              <p className="text-sm text-muted-foreground">无可定制参数</p>
            )}
          </TabsContent>
          
          <TabsContent value="metadata" className="space-y-4">
            {task.metadata ? (
              formatObject(task.metadata)
            ) : (
              <p className="text-sm text-muted-foreground">无元数据</p>
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
