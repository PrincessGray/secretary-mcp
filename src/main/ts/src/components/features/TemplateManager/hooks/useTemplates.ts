import { useState, useEffect } from 'react';
import { templateApi } from '@/services/api';
import { TaskTemplate, TemplateInfo } from '@/types/models';
import { useToast } from '@/lib/hooks/use-toast';

// 用于创建/编辑模板的表单数据类型
export interface TemplateFormData {
  name: string;
  description: string;
  connectionType: string;
  stdioCommand?: string;
  stdioArgs?: string | string[]; // 可以是字符串或字符串数组
  stdioEnv?: Record<string, string>;
  sseServerUrl?: string;
  sseAuthToken?: string;
  sseHeaders?: Record<string, string>;
  timeoutSeconds?: string | number;
  enableRoots?: boolean;
  enableSampling?: boolean;
  customizableParams: {
    name: string;
    displayName: string;
    description: string;
    type: string;
    required: boolean;
    defaultValue: any;
    category: "STDIO_ENV" | "STDIO_ARG" | "SSE_CONNECTION";
  }[];
  managementType?: string;
}

export function useTemplates() {
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('');
  const [selectedTemplate, setSelectedTemplate] = useState<TaskTemplate | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [apiResponse, setApiResponse] = useState<string>('');
  const [requestLog, setRequestLog] = useState<string>('');
  const { toast } = useToast();

  // 加载所有模板
  const loadTemplates = async () => {
    setIsLoading(true);
    setRequestLog('正在请求模板列表...');
    try {
      const data = await templateApi.fetchAll();
      setTemplates(data);
      setApiResponse(JSON.stringify(data, null, 2));
      return data;
    } catch (error: any) {
      const errorMessage = error.message || '未知错误';
      toast({
        title: "错误",
        description: "加载模板列表失败: " + errorMessage,
        variant: "destructive",
      });
      console.error('加载模板列表失败:', error);
      setApiResponse(JSON.stringify({ error: errorMessage }, null, 2));
    } finally {
      setIsLoading(false);
      setRequestLog('模板列表请求完成');
    }
    return [];
  };

  // 获取单个模板详情
  const getTemplateById = async (id: string) => {
    if (!id) return null;
    
    setIsLoading(true);
    setRequestLog(`正在请求模板 ID: ${id} 的详情...`);
    try {
      const data = await templateApi.fetchById(id);
      setSelectedTemplate(data);
      setApiResponse(JSON.stringify(data, null, 2));
      toast({
        title: "成功",
        description: "加载模板详情成功",
      });
      return data;
    } catch (error: any) {
      const errorMessage = error.message || '未知错误';
      toast({
        title: "错误",
        description: "加载模板详情失败: " + errorMessage,
        variant: "destructive",
      });
      console.error('加载模板详情失败:', error);
      setApiResponse(JSON.stringify({ error: errorMessage }, null, 2));
      return null;
    } finally {
      setIsLoading(false);
      setRequestLog(`模板 ID: ${id} 详情请求完成`);
    }
  };

  // 创建新模板
  const createTemplate = async (formData: TemplateFormData) => {
    setIsLoading(true);
    setRequestLog('正在创建新模板...');
    
    try {
      // 将表单数据转换为API需要的格式
      const templateData = buildTemplateData(formData);
      
      // 添加详细日志，查看发送的具体数据
      console.log('发送到服务器的模板数据:', JSON.stringify(templateData, null, 2));
      setRequestLog(`正在创建模板，请求数据: ${JSON.stringify(templateData, null, 2)}`);
      
      const data = await templateApi.create(templateData);
      setApiResponse(JSON.stringify(data, null, 2));
      toast({
        title: "成功",
        description: `成功创建模板 "${formData.name}"`,
      });
      await loadTemplates();
      return true;
    } catch (error: any) {
      // 增强错误日志，捕获服务器返回的详细错误信息
      const errorMessage = error.message || '未知错误';
      const responseData = error.response?.data;
      
      console.error('创建模板失败:', error);
      console.error('错误详情:', responseData);
      
      toast({
        title: "错误",
        description: "创建模板失败: " + errorMessage,
        variant: "destructive",
      });
      setApiResponse(JSON.stringify({ 
        error: errorMessage, 
        details: responseData || '无详细信息'
      }, null, 2));
      return false;
    } finally {
      setIsLoading(false);
      setRequestLog('创建模板请求完成');
    }
  };

  // 更新现有模板
  const updateTemplate = async (id: string, formData: TemplateFormData) => {
    if (!id) return false;
    
    setIsLoading(true);
    setRequestLog(`正在更新模板 ID: ${id}...`);
    
    try {
      // 将表单数据转换为API需要的格式
      const templateData = buildTemplateData(formData);
      
      const data = await templateApi.update(id, templateData);
      setApiResponse(JSON.stringify(data, null, 2));
      toast({
        title: "成功",
        description: `成功更新模板 "${formData.name}"`,
      });
      await loadTemplates();
      return true;
    } catch (error: any) {
      const errorMessage = error.message || '未知错误';
      toast({
        title: "错误",
        description: "更新模板失败: " + errorMessage,
        variant: "destructive",
      });
      console.error('更新模板失败:', error);
      setApiResponse(JSON.stringify({ error: errorMessage }, null, 2));
      return false;
    } finally {
      setIsLoading(false);
      setRequestLog(`模板 ID: ${id} 更新请求完成`);
    }
  };

  // 删除模板
  const deleteTemplate = async (id: string) => {
    if (!id) return false;
    
    setIsLoading(true);
    setRequestLog(`正在删除模板 ID: ${id}...`);
    
    try {
      await templateApi.delete(id);
      setApiResponse(JSON.stringify({ success: true, message: `模板 ID: ${id} 已成功删除` }, null, 2));
      toast({
        title: "成功",
        description: "成功删除模板",
      });
      
      // 如果删除的是当前选中的模板，重置选择
      if (id === selectedTemplateId) {
        setSelectedTemplateId('');
        setSelectedTemplate(null);
      }
      
      await loadTemplates();
      return true;
    } catch (error: any) {
      const errorMessage = error.message || '未知错误';
      toast({
        title: "错误",
        description: "删除模板失败: " + errorMessage,
        variant: "destructive",
      });
      console.error('删除模板失败:', error);
      setApiResponse(JSON.stringify({ error: errorMessage }, null, 2));
      return false;
    } finally {
      setIsLoading(false);
      setRequestLog(`模板 ID: ${id} 删除请求完成`);
    }
  };

  // 获取模板参数
  const getTemplateParams = async (templateId: string) => {
    if (!templateId) return null;
    
    setIsLoading(true);
    setRequestLog(`正在获取模板 ID: ${templateId} 的参数配置...`);
    
    try {
      const data = await templateApi.getTemplateParams(templateId);
      setApiResponse(JSON.stringify(data, null, 2));
      return data;
    } catch (error: any) {
      const errorMessage = error.message || '未知错误';
      toast({
        title: "错误",
        description: "获取模板参数失败: " + errorMessage,
        variant: "destructive",
      });
      console.error('获取模板参数失败:', error);
      setApiResponse(JSON.stringify({ error: errorMessage }, null, 2));
      return null;
    } finally {
      setIsLoading(false);
      setRequestLog(`模板 ID: ${templateId} 参数请求完成`);
    }
  };

  // 构建模板数据
  const buildTemplateData = (formData: TemplateFormData) => {
    // 使用后端期望的请求结构
    const requestData: any = {
      name: formData.name,
      description: formData.description,
      // 后端 Constants.ConnectionType.fromValue() 期望小写的连接类型
      connectionType: formData.connectionType.toLowerCase()
    };
    
    // 根据连接类型设置特定字段
    if (formData.connectionType.toLowerCase() === 'stdio') {
      requestData.stdioCommand = formData.stdioCommand;
      
      // 添加管理类型 - 确保匹配后端的枚举值
      if (formData.managementType) {
        // 确保管理类型是后端支持的枚举值
        const managementType = formData.managementType.toUpperCase();
        // 根据Constants.java中的ManagementType枚举定义
        if (['NPX', 'UVX'].includes(managementType)) {
          requestData.managementType = managementType;
        } else {
          // 默认使用NPX
          requestData.managementType = 'NPX';
        }
      }
      
      // 处理命令参数 - 确保不会有空数组或无效参数
      let args: string[] = [];
      if (formData.stdioArgs) {
        if (typeof formData.stdioArgs === 'string') {
          // 如果是字符串，按换行符拆分
          if (formData.stdioArgs.trim()) {
            args = formData.stdioArgs.split('\n')
              .map((arg: string) => arg.trim())
              .filter((arg: string) => arg !== '');
          }
        } else if (Array.isArray(formData.stdioArgs)) {
          // 如果已经是数组，过滤掉空值
          args = formData.stdioArgs.filter((arg: string) => arg && arg.trim() !== '');
        }
      }
      
      // 始终包含stdioArgs字段，即使是空数组
      requestData.stdioArgs = args;
      
      // 环境变量 - 确保包含必要的工作目录环境变量
      const envVars = { ...(formData.stdioEnv || {}) };
      
      // 如果没有设置工作目录，添加一个默认值（当前目录）
      if (!envVars['WORKING_DIR']) {
        envVars['WORKING_DIR'] = '.';
      }
      
      if (Object.keys(envVars).length > 0) {
        requestData.stdioEnv = envVars;
      }
    } else if (formData.connectionType.toLowerCase() === 'sse') {
      requestData.sseServerUrl = formData.sseServerUrl;
      
      if (formData.sseAuthToken) {
        requestData.sseAuthToken = formData.sseAuthToken;
      }
    }
    
    // 添加通用配置
    if (formData.timeoutSeconds) {
      // 确保是数字类型
      requestData.timeoutSeconds = typeof formData.timeoutSeconds === 'string' 
        ? parseInt(formData.timeoutSeconds) 
        : formData.timeoutSeconds;
    }
    
    if (formData.enableRoots !== undefined) {
      requestData.enableRoots = formData.enableRoots;
    }
    
    if (formData.enableSampling !== undefined) {
      requestData.enableSampling = formData.enableSampling;
    }
    
    // 处理自定义参数
    if (formData.customizableParams && formData.customizableParams.length > 0) {
      // 确保category格式正确，并且对defaultValue进行正确的类型处理
      requestData.customizableParams = formData.customizableParams.map(param => {
        // 确保category是枚举类型值之一
        let category: "STDIO_ENV" | "STDIO_ARG" | "SSE_CONNECTION" = param.category;
        if (typeof category === 'string') {
          const upperCategory = category.toUpperCase();
          if (upperCategory === 'STDIO_ENV' || upperCategory === 'STDIO_ARG' || upperCategory === 'SSE_CONNECTION') {
            category = upperCategory as "STDIO_ENV" | "STDIO_ARG" | "SSE_CONNECTION";
          } else {
            // 尝试修正无效的category
            if (upperCategory.includes('ENV')) category = 'STDIO_ENV';
            else if (upperCategory.includes('ARG')) category = 'STDIO_ARG';
            else if (upperCategory.includes('SSE')) category = 'SSE_CONNECTION';
            else category = 'STDIO_ENV'; // 默认
          }
        }
        
        // 根据类型处理defaultValue
        let defaultValue = param.defaultValue;
        if (category === 'STDIO_ARG' && typeof defaultValue === 'string') {
          // 对于命令行参数，确保defaultValue是布尔值
          defaultValue = defaultValue.toLowerCase() === 'true';
        } else if (category === 'STDIO_ENV' || category === 'SSE_CONNECTION') {
          // 确保这些类型的defaultValue是字符串
          if (defaultValue !== null && defaultValue !== undefined && typeof defaultValue !== 'string') {
            defaultValue = String(defaultValue);
          }
        }
        
        return {
          name: param.name,
          displayName: param.displayName,
          description: param.description,
          type: param.type,
          required: param.required,
          defaultValue: defaultValue,
          category: category
        };
      });
    } else {
      requestData.customizableParams = []; // 确保有空数组而不是undefined
    }
    
    return requestData;
  };

  // 初始加载
  useEffect(() => {
    loadTemplates();
  }, []);

  return {
    templates,
    selectedTemplateId,
    setSelectedTemplateId,
    selectedTemplate,
    isLoading,
    apiResponse,
    requestLog,
    loadTemplates,
    getTemplateById,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    getTemplateParams
  };
} 