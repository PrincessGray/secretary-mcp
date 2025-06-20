import { useState, useEffect } from 'react';
import { Secretary, SecretaryInfo } from '@/types/models';
import { secretaryApi, userSecretaryApi } from '@/services/api';
import { useToast } from '@/lib/hooks/use-toast';

// 定义返回类型，秘书映射关系
type UserSecretaryMappings = Record<string, string[]>;

export function useUserSecretaryMappings() {
  const [mappings, setMappings] = useState<UserSecretaryMappings>({});
  const [secretaries, setSecretaries] = useState<SecretaryInfo[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const { toast } = useToast();

  // 加载所有用户-秘书映射关系
  const loadMappings = async () => {
    setIsLoading(true);
    try {
      const data = await userSecretaryApi.getAllMappings();
      setMappings(data || {});
    } catch (error) {
      toast({
        title: "错误",
        description: "加载用户-秘书映射关系失败",
        variant: "destructive",
      });
      console.error('加载用户-秘书映射关系失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 加载所有秘书
  const loadSecretaries = async () => {
    setIsLoading(true);
    try {
      const data = await secretaryApi.fetchAll();
      setSecretaries(data || []);
    } catch (error) {
      toast({
        title: "错误",
        description: "加载秘书列表失败",
        variant: "destructive",
      });
      console.error('加载秘书列表失败:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 注册用户与秘书的关联
  const registerUserSecretary = async (userId: string, secretaryName: string) => {
    setIsLoading(true);
    try {
      await userSecretaryApi.register(userId, secretaryName);
      toast({
        title: "成功",
        description: `成功将用户 "${userId}" 关联到秘书 "${secretaryName}"`,
      });
      await loadMappings();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "注册用户-秘书关联失败",
        variant: "destructive",
      });
      console.error('注册用户-秘书关联失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 解除用户与特定秘书的关联
  const unregisterUserSecretary = async (userId: string, secretaryName: string) => {
    if (!window.confirm(`确定要解除用户 "${userId}" 与秘书 "${secretaryName}" 的关联吗？`)) {
      return false;
    }
    
    setIsLoading(true);
    try {
      await userSecretaryApi.unregister(userId, secretaryName);
      toast({
        title: "成功",
        description: `成功解除用户 "${userId}" 与秘书 "${secretaryName}" 的关联`,
      });
      await loadMappings();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "解除用户-秘书关联失败",
        variant: "destructive",
      });
      console.error('解除用户-秘书关联失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 解除用户所有秘书关联
  const unregisterAllUserSecretaries = async (userId: string) => {
    if (!window.confirm(`确定要解除用户 "${userId}" 的所有秘书关联吗？`)) {
      return false;
    }
    
    setIsLoading(true);
    try {
      await userSecretaryApi.unregisterAll(userId);
      toast({
        title: "成功",
        description: `成功解除用户 "${userId}" 的所有秘书关联`,
      });
      if (selectedUserId === userId) {
        setSelectedUserId(null);
      }
      await loadMappings();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "解除用户所有关联失败",
        variant: "destructive",
      });
      console.error('解除用户所有关联失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 切换秘书状态
  const toggleSecretaryStatus = async (id: string, active: boolean) => {
    setIsLoading(true);
    try {
      if (active) {
        await secretaryApi.activate(id);
        toast({ title: "成功", description: "秘书已激活" });
      } else {
        await secretaryApi.deactivate(id);
        toast({ title: "成功", description: "秘书已停用" });
      }
      await loadSecretaries();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "切换秘书状态失败",
        variant: "destructive",
      });
      console.error('切换秘书状态失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 初始加载
  useEffect(() => {
    loadMappings();
    loadSecretaries();
  }, []);

  return {
    mappings,
    secretaries,
    selectedUserId,
    setSelectedUserId,
    isLoading,
    registerUserSecretary,
    unregisterUserSecretary,
    unregisterAllUserSecretaries,
    toggleSecretaryStatus,
    refreshData: () => {
      loadMappings();
      loadSecretaries();
    }
  };
} 