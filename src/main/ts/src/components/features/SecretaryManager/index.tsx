import { useState, useEffect } from 'react';
import { PlusCircleIcon, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SecretaryCard } from './SecretaryCard';
import { TasksTable } from './TasksTable';
import { CreateSecretaryDialog } from './CreateSecretaryDialog';
import { CreateTaskDialog } from './CreateTaskDialog';
import { TaskDetailDialog } from './TaskDetailDialog';
import { useSecretaries } from './hooks/useSecretaries';
import { useTasks } from './hooks/useTasks';
import { RemoteTask } from '@/types/models';
import { useSearchParams } from 'react-router-dom';

export default function SecretaryManager() {
  // 获取URL查询参数
  const [searchParams] = useSearchParams();
  const secretaryIdFromUrl = searchParams.get('id');
  
  // 状态及钩子
  const { 
    secretaries, 
    selectedSecretaryId, 
    setSelectedSecretaryId, 
    isLoading: isSecretaryLoading,
    createSecretary,
    deleteSecretary,
    toggleSecretaryStatus,
    refreshSecretaries
  } = useSecretaries();
  
  const {
    tasks,
    templates,
    isLoading: isTaskLoading,
    createTask,
    deleteTask,
    toggleTaskStatus,
    refreshTasks
  } = useTasks(selectedSecretaryId);
  
  // 对话框状态
  const [createSecretaryOpen, setCreateSecretaryOpen] = useState(false);
  const [createTaskOpen, setCreateTaskOpen] = useState(false);
  const [taskDetailOpen, setTaskDetailOpen] = useState(false);
  const [selectedTask, setSelectedTask] = useState<RemoteTask | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  
  // 从URL参数中选中对应秘书
  useEffect(() => {
    if (secretaryIdFromUrl && secretaries.some(s => s.id === secretaryIdFromUrl)) {
      setSelectedSecretaryId(secretaryIdFromUrl);
    }
  }, [secretaryIdFromUrl, secretaries, setSelectedSecretaryId]);
  
  // 处理任务详情查看
  const handleViewTaskDetail = (task: RemoteTask) => {
    setSelectedTask(task);
    setTaskDetailOpen(true);
  };
  
  // 处理删除确认
  const handleDeleteSecretary = (id: string) => {
    if (window.confirm('确定要删除此秘书吗？此操作将同时删除所有关联的任务且不可恢复。')) {
      deleteSecretary(id);
    }
  };
  
  const handleDeleteTask = (id: string) => {
    if (window.confirm('确定要删除此任务吗？此操作不可恢复。')) {
      deleteTask(id);
    }
  };
  
  // 刷新所有数据
  const handleRefreshData = async () => {
    setIsRefreshing(true);
    try {
      await Promise.all([
        refreshSecretaries(),
        selectedSecretaryId ? refreshTasks() : Promise.resolve()
      ]);
    } finally {
      setIsRefreshing(false);
    }
  };
  
  // 获取当前秘书名称
  const getCurrentSecretaryName = () => {
    if (!selectedSecretaryId) return '';
    const secretary = secretaries.find(s => s.id === selectedSecretaryId);
    return secretary ? secretary.name : '';
  };
  
  const isLoading = isSecretaryLoading || isTaskLoading || isRefreshing;
  
  return (
    <div className="container mx-auto py-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">秘书任务管理</h1>
        
        <Button 
          variant="outline"
          size="sm"
          onClick={handleRefreshData}
          disabled={isLoading}
          className="gap-2"
        >
          <RefreshCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
          刷新数据
        </Button>
      </div>
      
      <div className="grid md:grid-cols-12 gap-6">
        {/* 左侧 - 秘书列表 */}
        <div className="md:col-span-4 space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-semibold">秘书列表</h2>
            <Button 
              onClick={() => setCreateSecretaryOpen(true)}
              disabled={isLoading}
              size="sm"
            >
              <PlusCircleIcon className="h-4 w-4 mr-1" />
              创建新秘书
            </Button>
          </div>
          
          <div className="border rounded-lg p-4 bg-card">
            {secretaries.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                {isLoading ? "加载中..." : "暂无秘书，请创建新秘书"}
              </div>
            ) : (
              <div className="space-y-2 max-h-[500px] overflow-y-auto">
                {secretaries.map(secretary => (
                  <SecretaryCard
                    key={secretary.id}
                    secretary={secretary}
                    isSelected={selectedSecretaryId === secretary.id}
                    onSelect={() => setSelectedSecretaryId(secretary.id)}
                    onToggleStatus={(active) => toggleSecretaryStatus(secretary.id, active)}
                    onDelete={() => handleDeleteSecretary(secretary.id)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
        
        {/* 右侧 - 任务列表 */}
        <div className="md:col-span-8 space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-semibold">
              {selectedSecretaryId ? `${getCurrentSecretaryName()} 的任务` : '任务列表'}
            </h2>
            <Button 
              onClick={() => setCreateTaskOpen(true)}
              disabled={isLoading || !selectedSecretaryId}
              size="sm"
            >
              <PlusCircleIcon className="h-4 w-4 mr-1" />
              创建新任务
            </Button>
          </div>
          
          {!selectedSecretaryId ? (
            <div className="border rounded-lg p-8 text-center text-muted-foreground bg-card">
              请先从左侧选择一个秘书
            </div>
          ) : (
            <TasksTable
              tasks={tasks}
              templates={templates}
              onToggleStatus={toggleTaskStatus}
              onDelete={handleDeleteTask}
              onViewDetails={handleViewTaskDetail}
            />
          )}
        </div>
      </div>
      
      {/* 对话框 */}
      <CreateSecretaryDialog
        isOpen={createSecretaryOpen}
        onClose={() => setCreateSecretaryOpen(false)}
        onSubmit={async (name, description) => {
          const success = await createSecretary(name, description);
          if (success) setCreateSecretaryOpen(false);
          return success;
        }}
        isLoading={isLoading}
      />
      
      <CreateTaskDialog
        isOpen={createTaskOpen}
        onClose={() => setCreateTaskOpen(false)}
        onSubmit={async (data) => {
          const success = await createTask(data);
          if (success) setCreateTaskOpen(false);
          return success;
        }}
        templates={templates}
        secretaryId={selectedSecretaryId}
        isLoading={isLoading}
      />
      
      <TaskDetailDialog
        isOpen={taskDetailOpen}
        onClose={() => setTaskDetailOpen(false)}
        task={selectedTask}
        templates={templates}
      />
    </div>
  );
} 