import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Card, CardContent } from "@/components/ui/card";
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Trash2 } from "lucide-react";

interface CustomParam {
  name: string;
  displayName: string;
  description: string;
  type: string;
  required: boolean;
  defaultValue: any;
  category: "STDIO_ENV" | "STDIO_ARG" | "SSE_CONNECTION";
}

interface CustomParamsFormProps {
  params: CustomParam[];
  onAddParam: (param: CustomParam) => void;
  onRemoveParam: (index: number) => void;
}

export function CustomParamsForm({ 
  params, 
  onAddParam, 
  onRemoveParam 
}: CustomParamsFormProps) {
  const [paramType, setParamType] = useState<"STDIO_ENV" | "STDIO_ARG" | "SSE_CONNECTION">("STDIO_ENV");
  const [formValues, setFormValues] = useState<Omit<CustomParam, "type">>({
    name: "",
    displayName: "",
    description: "",
    required: false,
    defaultValue: "",
    category: "STDIO_ENV"
  });

  const handleInputChange = (field: string, value: any) => {
    setFormValues({
      ...formValues,
      [field]: value
    });
  };

  const addParameter = () => {
    // 基本验证
    if (!formValues.name || !formValues.displayName) {
      alert('参数名称和显示名称不能为空');
      return;
    }

    // 根据参数类别设置类型
    let type = "string";
    if (paramType === "STDIO_ARG") {
      type = "boolean";
    }

    // 创建新参数对象
    const newParam: CustomParam = {
      ...formValues,
      type,
      category: paramType
    };

    // 添加到参数列表
    onAddParam(newParam);

    // 重置表单
    setFormValues({
      name: "",
      displayName: "",
      description: "",
      required: false,
      defaultValue: "",
      category: paramType
    });
  };

  const getCategoryName = (category: string) => {
    switch (category) {
      case "STDIO_ENV": return "STDIO环境变量";
      case "STDIO_ARG": return "STDIO命令行参数(仅启用/禁用)";
      case "SSE_CONNECTION": return "SSE连接参数";
      default: return category;
    }
  };

  return (
    <div className="space-y-8 w-full">
      <div className="space-y-6">
        <h3 className="text-lg font-medium">添加可定制参数</h3>
        
        <div className="space-y-2">
          <Label htmlFor="paramCategory">参数类别</Label>
          <Select
            value={paramType}
            onValueChange={(value: "STDIO_ENV" | "STDIO_ARG" | "SSE_CONNECTION") => {
              setParamType(value);
              setFormValues({
                ...formValues,
                category: value
              });
            }}
          >
            <SelectTrigger id="paramCategory">
              <SelectValue placeholder="选择参数类别" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="STDIO_ENV">STDIO环境变量</SelectItem>
              <SelectItem value="STDIO_ARG">STDIO命令行参数(开关)</SelectItem>
              <SelectItem value="SSE_CONNECTION">SSE连接参数</SelectItem>
            </SelectContent>
          </Select>
        </div>
        
        {paramType === "SSE_CONNECTION" && (
          <div className="space-y-2">
            <Label htmlFor="sseParamType">SSE参数类型</Label>
            <Select
              value={formValues.name}
              onValueChange={(value) => {
                const defaultDisplayName = value === "serverUrl" ? "服务器URL" : "Bearer Token";
                const defaultDescription = value === "serverUrl" ? 
                  "SSE服务器的URL地址" : "用于SSE服务器认证的Bearer Token";
                
                setFormValues({
                  ...formValues,
                  name: value,
                  displayName: defaultDisplayName,
                  description: defaultDescription
                });
              }}
            >
              <SelectTrigger id="sseParamType">
                <SelectValue placeholder="选择SSE参数类型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="serverUrl">服务器URL</SelectItem>
                <SelectItem value="bearerToken">Bearer Token</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-sm text-muted-foreground mt-1">
              SSE只支持两种连接参数: serverUrl和bearerToken
            </p>
          </div>
        )}
        
        <div className="space-y-2">
          <Label htmlFor="paramName">参数名称 *</Label>
          <Input
            id="paramName"
            value={formValues.name}
            onChange={(e) => handleInputChange("name", e.target.value)}
            placeholder={
              paramType === "STDIO_ENV" ? "例如: API_KEY" : 
              paramType === "STDIO_ARG" ? "例如: --verbose" : 
              "例如: serverUrl"
            }
            readOnly={paramType === "SSE_CONNECTION"}
            required
          />
          <p className="text-sm text-muted-foreground mt-1">
            {paramType === "STDIO_ENV" ? "环境变量名" : 
             paramType === "STDIO_ARG" ? "命令行参数" : 
             "连接参数名(serverUrl或bearerToken)"}
          </p>
        </div>
        
        <div className="space-y-2">
          <Label htmlFor="displayName">显示名称 *</Label>
          <Input
            id="displayName"
            value={formValues.displayName}
            onChange={(e) => handleInputChange("displayName", e.target.value)}
            placeholder="在用户界面显示的友好名称"
            required
          />
          <p className="text-sm text-muted-foreground mt-1">
            在用户界面显示的友好名称
          </p>
        </div>
        
        <div className="space-y-2">
          <Label htmlFor="description">描述</Label>
          <Textarea
            id="description"
            value={formValues.description}
            onChange={(e) => handleInputChange("description", e.target.value)}
            placeholder="描述此参数的作用"
            rows={2}
          />
        </div>
        
        {paramType !== "STDIO_ARG" ? (
          <>
            <div className="space-y-2">
              <Label htmlFor="defaultValue">默认值</Label>
              <Input
                id="defaultValue"
                value={formValues.defaultValue}
                onChange={(e) => handleInputChange("defaultValue", e.target.value)}
                placeholder="默认值"
              />
            </div>
            
            <div className="flex items-center space-x-2">
              <Switch
                id="required"
                checked={formValues.required}
                onCheckedChange={(checked) => handleInputChange("required", checked)}
              />
              <Label htmlFor="required">是否必填</Label>
            </div>
          </>
        ) : (
          <div className="flex items-center space-x-2">
            <Switch
              id="enabledByDefault"
              checked={formValues.defaultValue === true}
              onCheckedChange={(checked) => handleInputChange("defaultValue", checked)}
            />
            <Label htmlFor="enabledByDefault">
              默认是否启用: {formValues.defaultValue === true ? "启用" : "禁用"}
            </Label>
          </div>
        )}
        
        <Button 
          onClick={addParameter}
          className="mt-4"
        >
          添加参数
        </Button>
      </div>
      
      <div className="space-y-4">
        <h3 className="text-lg font-medium">已添加的可定制参数</h3>
        
        <Card>
          <CardContent className="p-6">
            {params.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-8">
                还没有添加参数
              </p>
            ) : (
              <div className="space-y-4">
                {params.map((param, index) => (
                  <Card key={index} className="relative">
                    <Button
                      className="absolute top-2 right-2"
                      variant="ghost"
                      size="sm"
                      onClick={() => onRemoveParam(index)}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                    
                    <CardContent className="p-4">
                      <div className="flex flex-wrap items-center gap-2 mb-2">
                        <h4 className="text-base font-semibold">
                          {param.displayName}
                        </h4>
                        <span className="text-sm text-muted-foreground">
                          ({param.name})
                        </span>
                      </div>
                      
                      <div className="flex flex-wrap gap-2 mb-2">
                        <Badge variant="outline">{getCategoryName(param.category)}</Badge>
                        <Badge variant="outline">类型: {param.type}</Badge>
                      </div>
                      
                      {param.description && (
                        <p className="text-sm text-muted-foreground mb-2">
                          {param.description}
                        </p>
                      )}
                      
                      <div className="text-sm grid grid-cols-2 gap-2">
                        <div>
                          <span className="font-medium">默认值: </span>
                          <span>
                            {param.defaultValue !== null && param.defaultValue !== "" ? 
                              (typeof param.defaultValue === 'boolean' ? 
                                (param.defaultValue ? '启用' : '禁用') : 
                                param.defaultValue) : 
                              '无'}
                          </span>
                        </div>
                        <div>
                          <span className="font-medium">必填: </span>
                          <span>{param.required ? '是' : '否'}</span>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
} 