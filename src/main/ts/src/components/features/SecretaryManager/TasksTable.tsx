import { RemoteTask, TemplateInfo, TaskStatus } from '@/types/models';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { InfoIcon, Trash2 } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

interface TasksTableProps {
  tasks: RemoteTask[];
  templates: TemplateInfo[];
  onToggleStatus: (taskId: string, active: boolean) => void;
  onDelete: (taskId: string) => void;
  onViewDetails: (task: RemoteTask) => void;
}

export function TasksTable({ 
  tasks, 
  templates, 
  onToggleStatus, 
  onDelete, 
  onViewDetails 
}: TasksTableProps) {
  // 获取模板名称
  const getTemplateName = (templateId: string) => {
    const template = templates.find(t => t.id === templateId);
    return template ? template.name : templateId;
  };

  return (
    <div className="border rounded-lg overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>任务名称</TableHead>
            <TableHead>模板</TableHead>
            <TableHead className="w-[120px]">状态</TableHead>
            <TableHead className="w-[120px]">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tasks.length === 0 ? (
            <TableRow>
              <TableCell colSpan={4} className="text-center py-6 text-muted-foreground">
                暂无任务，请创建新任务
              </TableCell>
            </TableRow>
          ) : (
            tasks.map(task => (
              <TableRow key={task.id}>
                <TableCell className="font-medium">{task.name}</TableCell>
                <TableCell>{getTemplateName(task.templateId)}</TableCell>
                <TableCell>
                  <div className="flex items-center space-x-2">
                    <Switch 
                      checked={task.status === TaskStatus.ACTIVE} 
                      onCheckedChange={(checked) => onToggleStatus(task.id, checked)}
                    />
                    <span className="text-xs">
                      {task.status === TaskStatus.ACTIVE ? '已激活' : '未激活'}
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex space-x-2">
                    <Button 
                      variant="outline" 
                      size="sm" 
                      className="h-8 px-2"
                      onClick={() => onViewDetails(task)}
                    >
                      <InfoIcon className="h-3.5 w-3.5 mr-1" />
                      详情
                    </Button>
                    <Button 
                      variant="outline" 
                      size="sm" 
                      className="h-8 px-2"
                      onClick={() => onDelete(task.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5 mr-1" />
                      删除
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}
