import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from "@/components/ui/select";

interface FormData {
  name: string;
  description: string;
  connectionType: string;
  [key: string]: any;
}

interface BasicInfoFormProps {
  formData: FormData;
  onChange: (field: string, value: any) => void;
}

export function BasicInfoForm({ formData, onChange }: BasicInfoFormProps) {
  return (
    <div className="space-y-6 w-full">
      <div className="space-y-2">
        <Label htmlFor="name">模板名称 *</Label>
        <Input
          id="name"
          value={formData.name}
          onChange={(e) => onChange('name', e.target.value)}
          placeholder="输入模板名称"
          required
        />
      </div>
      
      <div className="space-y-2">
        <Label htmlFor="description">模板描述</Label>
        <Textarea
          id="description"
          value={formData.description}
          onChange={(e) => onChange('description', e.target.value)}
          placeholder="输入模板描述"
          rows={3}
        />
      </div>
      
      <div className="space-y-2">
        <Label htmlFor="connectionType">连接类型 *</Label>
        <Select
          value={formData.connectionType}
          onValueChange={(value) => onChange('connectionType', value)}
        >
          <SelectTrigger id="connectionType">
            <SelectValue placeholder="选择连接类型" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="STDIO">标准输入输出 (STDIO)</SelectItem>
            <SelectItem value="SSE">服务器发送事件 (SSE)</SelectItem>
          </SelectContent>
        </Select>
        <p className="text-sm text-muted-foreground mt-1">
          选择模板使用的连接类型，不同连接类型有不同的配置参数。
        </p>
      </div>
    </div>
  );
} 