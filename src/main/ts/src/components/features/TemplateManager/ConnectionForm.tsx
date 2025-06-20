import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { PlusCircle, Trash2 } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";

interface FormData {
  connectionType: string;
  stdioCommand?: string;
  stdioArgs?: string | string[];
  stdioEnv?: Record<string, string>;
  sseServerUrl?: string;
  sseAuthToken?: string;
  timeoutSeconds?: string | number;
  enableRoots?: boolean;
  enableSampling?: boolean;
  managementType?: string;
  [key: string]: any;
}

interface ConnectionFormProps {
  formData: FormData;
  onChange: (field: string, value: any) => void;
  onAddEnvVar: (name: string, value: string) => void;
  onRemoveEnvVar: (name: string) => void;
}

export function ConnectionForm({
  formData,
  onChange,
  onAddEnvVar,
  onRemoveEnvVar
}: ConnectionFormProps) {
  // 新环境变量状态
  const [newEnvName, setNewEnvName] = useState("");
  const [newEnvValue, setNewEnvValue] = useState("");
  
  // 新请求头状态
  const [newHeaderName, setNewHeaderName] = useState("");
  const [newHeaderValue, setNewHeaderValue] = useState("");
  
  // 处理添加环境变量
  const handleAddEnvVar = () => {
    if (newEnvName && newEnvValue) {
      onAddEnvVar(newEnvName, newEnvValue);
      setNewEnvName("");
      setNewEnvValue("");
    }
  };
  
  // 处理添加请求头
  const handleAddHeader = () => {
    if (newHeaderName && newHeaderValue) {
      // This function is removed as per the instructions
    }
  };
  
  return (
    <div className="space-y-8 w-full">
      {formData.connectionType === "STDIO" ? (
        // STDIO连接配置
        <div className="space-y-6">
          <h3 className="text-lg font-medium">STDIO连接配置</h3>
          
          <div className="space-y-2">
            <Label htmlFor="stdioCommand">命令 *</Label>
            <Input
              id="stdioCommand"
              value={formData.stdioCommand || ""}
              onChange={(e) => onChange("stdioCommand", e.target.value)}
              placeholder="输入命令"
              required
            />
            <p className="text-sm text-muted-foreground mt-1">
              执行的命令，例如 python, node, java 等
            </p>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="managementType">管理类型</Label>
            <Select
              value={formData.managementType || "NPX"}
              onValueChange={(value) => onChange("managementType", value)}
            >
              <SelectTrigger id="managementType">
                <SelectValue placeholder="选择管理类型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NPX">NPX</SelectItem>
                <SelectItem value="UVX">UVX</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-sm text-muted-foreground mt-1">
              命令的管理方式，默认为NPX
            </p>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="workingDir">工作目录</Label>
            <Input
              id="workingDir"
              value={formData.stdioEnv?.['WORKING_DIR'] || '.'}
              onChange={(e) => {
                const value = e.target.value.trim() || '.';
                onAddEnvVar('WORKING_DIR', value);
              }}
              placeholder="执行命令的工作目录，默认为当前目录"
            />
            <p className="text-sm text-muted-foreground mt-1">
              命令执行的工作目录路径（相对或绝对路径）
            </p>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="stdioArgs">命令参数</Label>
            <div className="space-y-4">
              {Array.isArray(formData.stdioArgs) && formData.stdioArgs.map((arg, index) => (
                <div key={index} className="flex gap-2">
                  <Input
                    value={arg}
                    onChange={(e) => {
                      const newArgs = [...formData.stdioArgs as string[]];
                      newArgs[index] = e.target.value;
                      onChange("stdioArgs", newArgs);
                    }}
                    placeholder={`参数 ${index + 1}`}
                    className="flex-1"
                  />
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={() => {
                      const newArgs = [...formData.stdioArgs as string[]];
                      newArgs.splice(index, 1);
                      onChange("stdioArgs", newArgs);
                    }}
                  >
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              ))}
              <Button
                variant="outline"
                onClick={() => {
                  const newArgs = Array.isArray(formData.stdioArgs) 
                    ? [...formData.stdioArgs, ""]
                    : [""];
                  onChange("stdioArgs", newArgs);
                }}
                className="w-full"
              >
                <PlusCircle className="h-4 w-4 mr-2" />
                添加参数
              </Button>
            </div>
            <p className="text-sm text-muted-foreground mt-1">
              命令执行时使用的参数列表
            </p>
          </div>
          
          <div className="space-y-4">
            <div className="flex justify-between items-center">
              <Label>环境变量</Label>
            </div>
            
            <Card>
              <CardContent className="p-4">
                {Object.entries(formData.stdioEnv || {}).length > 0 ? (
                  <ul className="space-y-2 max-h-[200px] overflow-y-auto">
                    {Object.entries(formData.stdioEnv || {}).map(([name, value]) => (
                      <li key={name} className="flex justify-between items-center p-2 border rounded-md">
                        <div>
                          <span className="font-medium">{name}</span>
                          <span className="text-muted-foreground ml-2">{value}</span>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => onRemoveEnvVar(name)}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-muted-foreground text-center py-4">
                    没有环境变量
                  </p>
                )}
                
                <div className="grid grid-cols-12 gap-2 mt-4">
                  <div className="col-span-5">
                    <Input
                      placeholder="环境变量名"
                      value={newEnvName}
                      onChange={(e) => setNewEnvName(e.target.value)}
                    />
                  </div>
                  <div className="col-span-5">
                    <Input
                      placeholder="环境变量值"
                      value={newEnvValue}
                      onChange={(e) => setNewEnvValue(e.target.value)}
                    />
                  </div>
                  <div className="col-span-2">
                    <Button
                      variant="outline"
                      onClick={handleAddEnvVar}
                      className="w-full"
                    >
                      <PlusCircle className="h-4 w-4 mr-1" />
                      添加
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      ) : (
        // SSE连接配置
        <div className="space-y-6">
          <h3 className="text-lg font-medium">SSE连接配置</h3>
          
          <div className="space-y-2">
            <Label htmlFor="sseServerUrl">服务器URL *</Label>
            <Input
              id="sseServerUrl"
              value={formData.sseServerUrl || ""}
              onChange={(e) => onChange("sseServerUrl", e.target.value)}
              placeholder="输入服务器URL"
              required
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="sseAuthToken">Bearer Token</Label>
            <Input
              id="sseAuthToken"
              value={formData.sseAuthToken || ""}
              onChange={(e) => onChange("sseAuthToken", e.target.value)}
              placeholder="输入认证Token"
            />
            <p className="text-sm text-muted-foreground mt-1">
              用于服务器认证的Bearer Token
            </p>
          </div>
        </div>
      )}
      
      <Separator />
      
      {/* 通用配置部分 */}
      <div className="space-y-6">
        <h3 className="text-lg font-medium">通用配置</h3>
        
        <div className="space-y-2">
          <Label htmlFor="timeoutSeconds">超时时间 (秒)</Label>
          <Input
            id="timeoutSeconds"
            type="number"
            value={formData.timeoutSeconds || ""}
            onChange={(e) => onChange("timeoutSeconds", e.target.value)}
            placeholder="默认: 60"
            min={1}
          />
          <p className="text-sm text-muted-foreground mt-1">
            连接的超时时间，超过此时间将自动断开
          </p>
        </div>
        
        <div className="flex items-center space-x-2">
          <Switch
            id="enableRoots"
            checked={!!formData.enableRoots}
            onCheckedChange={(checked) => onChange("enableRoots", checked)}
          />
          <Label htmlFor="enableRoots">启用Roots功能</Label>
        </div>
        
        <div className="flex items-center space-x-2">
          <Switch
            id="enableSampling"
            checked={!!formData.enableSampling}
            onCheckedChange={(checked) => onChange("enableSampling", checked)}
          />
          <Label htmlFor="enableSampling">启用采样功能</Label>
        </div>
      </div>
    </div>
  );
} 