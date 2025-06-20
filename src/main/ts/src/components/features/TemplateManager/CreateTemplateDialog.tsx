import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { BasicInfoForm } from "./BasicInfoForm";
import { ConnectionForm } from "./ConnectionForm";
import { CustomParamsForm } from "./CustomParamsForm";
import { TemplateFormData } from "./hooks/useTemplates";

// 模板对话框属性
interface CreateTemplateDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (formData: TemplateFormData) => Promise<boolean>;
  isLoading: boolean;
}

export function CreateTemplateDialog({
  isOpen,
  onClose,
  onSubmit,
  isLoading
}: CreateTemplateDialogProps) {
  // 表单数据状态
  const [formData, setFormData] = useState<TemplateFormData>({
    name: "",
    description: "",
    connectionType: "STDIO",
    stdioCommand: "",
    stdioArgs: [],
    stdioEnv: {
      // 默认添加工作目录环境变量
      'WORKING_DIR': '.'
    },
    managementType: "NPX", // 使用后端支持的枚举值
    sseServerUrl: "",
    sseAuthToken: "",
    timeoutSeconds: "",
    enableRoots: false,
    enableSampling: false,
    customizableParams: []
  });
  
  // 选项卡状态
  const [activeTab, setActiveTab] = useState("basic");
  
  // 表单字段更新
  const handleFormChange = (field: string, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
  };
  
  // 添加环境变量
  const addEnvVar = (name: string, value: string) => {
    if (!name || !value) return;
    
    setFormData(prev => ({
      ...prev,
      stdioEnv: {
        ...prev.stdioEnv,
        [name]: value
      }
    }));
  };
  
  // 删除环境变量
  const removeEnvVar = (name: string) => {
    if (!formData.stdioEnv) return;
    
    setFormData(prev => {
      const newEnv = { ...prev.stdioEnv };
      delete newEnv[name];
      return {
        ...prev,
        stdioEnv: newEnv
      };
    });
  };
  
  // 添加自定义参数
  const addCustomParam = (param: any) => {
    setFormData(prev => ({
      ...prev,
      customizableParams: [...prev.customizableParams, param]
    }));
  };
  
  // 删除自定义参数
  const removeCustomParam = (index: number) => {
    setFormData(prev => ({
      ...prev,
      customizableParams: prev.customizableParams.filter((_, i) => i !== index)
    }));
  };

  // 处理提交
  const handleSubmit = async () => {
    // 基本验证
    if (!formData.name) {
      alert("模板名称不能为空");
      setActiveTab("basic");
      return;
    }

    const connType = formData.connectionType.toUpperCase();
    
    if (connType === "STDIO" && !formData.stdioCommand) {
      alert("STDIO命令不能为空");
      setActiveTab("connection");
      return;
    }

    if (connType === "SSE" && !formData.sseServerUrl) {
      alert("SSE服务器URL不能为空");
      setActiveTab("connection");
      return;
    }

    // 确保connectionType始终为大写
    const submittingData = {
      ...formData,
      connectionType: connType
    };

    const success = await onSubmit(submittingData);
    if (success) {
      // 重置表单
      setFormData({
        name: "",
        description: "",
        connectionType: "STDIO",
        stdioCommand: "",
        stdioArgs: [],
        stdioEnv: {
          // 默认添加工作目录环境变量
          'WORKING_DIR': '.'
        },
        managementType: "NPX",
        sseServerUrl: "",
        sseAuthToken: "",
        timeoutSeconds: "",
        enableRoots: false,
        enableSampling: false,
        customizableParams: []
      });
      setActiveTab("basic");
    }
  };
  
  // 对话框关闭回调
  const handleDialogClose = () => {
    onClose();
    // 延迟重置，避免关闭动画过程中看到表单重置
    setTimeout(() => {
      setFormData({
        name: "",
        description: "",
        connectionType: "STDIO",
        stdioCommand: "",
        stdioArgs: [],
        stdioEnv: {
          // 默认添加工作目录环境变量
          'WORKING_DIR': '.'
        },
        managementType: "NPX",
        sseServerUrl: "",
        sseAuthToken: "",
        timeoutSeconds: "",
        enableRoots: false,
        enableSampling: false,
        customizableParams: []
      });
      setActiveTab("basic");
    }, 300);
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleDialogClose()}>
      <DialogContent className="sm:max-w-[800px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>创建新模板</DialogTitle>
        </DialogHeader>
        
        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
          <TabsList className="grid grid-cols-3">
            <TabsTrigger value="basic">基本信息</TabsTrigger>
            <TabsTrigger value="connection">连接配置</TabsTrigger>
            <TabsTrigger value="params">可定制参数</TabsTrigger>
          </TabsList>
          
          <TabsContent value="basic" className="space-y-4 py-4">
            <BasicInfoForm 
              formData={formData} 
              onChange={handleFormChange} 
            />
          </TabsContent>
          
          <TabsContent value="connection" className="space-y-4 py-4">
            <ConnectionForm 
              formData={formData} 
              onChange={handleFormChange}
              onAddEnvVar={addEnvVar}
              onRemoveEnvVar={removeEnvVar}
            />
          </TabsContent>
          
          <TabsContent value="params" className="space-y-4 py-4">
            <CustomParamsForm 
              params={formData.customizableParams}
              onAddParam={addCustomParam}
              onRemoveParam={removeCustomParam}
            />
          </TabsContent>
        </Tabs>
        
        <DialogFooter className="mt-6">
          <Button 
            variant="outline" 
            onClick={handleDialogClose}
            disabled={isLoading}
          >
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={isLoading}
          >
            {isLoading ? "创建中..." : "创建模板"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
} 