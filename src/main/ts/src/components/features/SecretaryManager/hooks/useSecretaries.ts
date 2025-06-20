import { useState, useEffect } from 'react';
import { Secretary, SecretaryInfo } from '@/types/models';
import { secretaryApi } from '@/services/api';
import { useToast } from '@/lib/hooks/use-toast';

export function useSecretaries() {
  const [secretaries, setSecretaries] = useState<SecretaryInfo[]>([]);
  const [selectedSecretaryId, setSelectedSecretaryId] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const { toast } = useToast();

  // 加载秘书列表
  const loadSecretaries = async (skipAutoSelect = false) => {
    setIsLoading(true);
    try {
      const data = await secretaryApi.fetchAll();
      
      // 按照秘书名称字母顺序排序
      const sortedData = [...data].sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
      
      setSecretaries(sortedData);
      
      // 如果有秘书且未选择且不跳过自动选择，默认选第一个
      if (sortedData.length > 0 && !selectedSecretaryId && !skipAutoSelect) {
        setSelectedSecretaryId(sortedData[0].id);
      }
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

  // 创建新秘书
  const createSecretary = async (name: string, description = '') => {
    setIsLoading(true);
    try {
      await secretaryApi.create(name, description);
      toast({
        title: "成功",
        description: `成功创建秘书 "${name}"`,
      });
      await loadSecretaries();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "创建秘书失败",
        variant: "destructive",
      });
      console.error('创建秘书失败:', error);
      return false;
    } finally {
      setIsLoading(false);
    }
  };

  // 删除秘书
  const deleteSecretary = async (id: string) => {
    setIsLoading(true);
    try {
      await secretaryApi.delete(id);
      toast({
        title: "成功",
        description: "成功删除秘书",
      });
      
      // 如果删除的是当前选中的秘书，重置选择
      if (id === selectedSecretaryId) {
        setSelectedSecretaryId('');
      }
      
      await loadSecretaries();
      return true;
    } catch (error) {
      toast({
        title: "错误",
        description: "删除秘书失败",
        variant: "destructive",
      });
      console.error('删除秘书失败:', error);
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
    loadSecretaries();
  }, []);

  return {
    secretaries,
    selectedSecretaryId,
    setSelectedSecretaryId,
    isLoading,
    createSecretary,
    deleteSecretary,
    toggleSecretaryStatus,
    refreshSecretaries: (skipAutoSelect = false) => loadSecretaries(skipAutoSelect)
  };
}
