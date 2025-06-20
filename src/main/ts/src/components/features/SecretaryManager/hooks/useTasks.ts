import { useState, useEffect } from 'react';
import { RemoteTask, TemplateInfo } from '@/types/models';
import { taskApi, templateApi } from '@/services/api';
import { useToast } from '@/lib/hooks/use-toast';

export function useTasks(secretaryId: string) {
  const [tasks, setTasks] = useState<RemoteTask[]>([]);
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [selectedTask, setSelectedTask] = useState<RemoteTask | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const { toast } = useToast();

  // 加载任务列表
  const loadTasks = async () => {
    if (!secretaryId) return;
    
    setIsLoading(true);
    try {
      const data = await taskApi.fetchBySecretaryId(secretaryId);
      setTasks(data);
    } catch (error) {
      toast({
        title: "错误",
        description: "加载任务列表失败",
        variant: "destructive",
      });
      console.error('加载任务列表失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 加载模板列表
  const loadTemplates = async () => {
    setIsLoading(true);
    try {
      const data = await templateApi.fetchAll();
      setTemplates(data);
    } catch (error) {
      toast({
        title: "错误",
        description: "加载模板列表失败",
        variant: "destructive",
      });
      console.error('加载模板列表失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 创建任务（修改为两步过程）
  const createTask = async (data: {
    secretaryId: string;
    templateId: string;
    name: string;
    customParams?: Record<string, any>;
  }) => {
    setIsLoading(true);
    try {
      // 步骤1: 创建基本任务
      const taskData = {
        secretaryId: data.secretaryId,
        templateId: data.templateId,
        name: data.name
      };
      
      const createdTask = await taskApi.create(taskData);
      
      // 步骤2: 如果有自定义参数，再应用这些参数
      if (data.customParams && Object.keys(data.customParams).length > 0) {
        await taskApi.updateCustomParams(
          createdTask.id, 
          data.secretaryId, 
          data.customParams
        );
      }
      
      toast({
        title: "成功",
        description: `成功创建任务 "${data.name}"`,
      });
      
      await loadTasks();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "创建任务失败",
        variant: "destructive",
      });
      console.error('创建任务失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 删除任务
  const deleteTask = async (taskId: string) => {
    setIsLoading(true);
    try {
      await taskApi.delete(taskId, secretaryId);
      toast({
        title: "成功",
        description: "成功删除任务",
      });
      await loadTasks();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "删除任务失败",
        variant: "destructive",
      });
      console.error('删除任务失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 切换任务状态
  const toggleTaskStatus = async (taskId: string, active: boolean) => {
    setIsLoading(true);
    try {
      if (active) {
        await taskApi.activate(taskId, secretaryId);
        toast({ title: "成功", description: "任务已激活" });
      } else {
        await taskApi.deactivate(taskId, secretaryId);
        toast({ title: "成功", description: "任务已停用" });
      }
      await loadTasks();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "切换任务状态失败",
        variant: "destructive",
      });
      console.error('切换任务状态失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 当secretaryId变化时加载任务
  useEffect(() => {
    if (secretaryId) {
      loadTasks();
    } else {
      setTasks([]);
    }
  }, [secretaryId]);

  // 初始加载模板
  useEffect(() => {
    loadTemplates();
  }, []);

  return {
    tasks,
    templates,
    selectedTask,
    setSelectedTask,
    isLoading,
    createTask,
    deleteTask,
    toggleTaskStatus,
    refreshTasks: loadTasks
  };
} 