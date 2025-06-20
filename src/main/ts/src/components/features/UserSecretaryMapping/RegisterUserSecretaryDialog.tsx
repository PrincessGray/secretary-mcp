import { useState, useEffect } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SecretaryInfo } from "@/types/models";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

interface RegisterUserSecretaryDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (userId: string, secretaryName: string) => Promise<boolean>;
  secretaries: SecretaryInfo[];
  isLoading: boolean;
  onToggleSecretaryStatus: (id: string, active: boolean) => Promise<boolean>;
  showUserIdField?: boolean;
  userIdValue?: string;
}

export function RegisterUserSecretaryDialog({ 
  isOpen, 
  onClose, 
  onSubmit, 
  secretaries, 
  isLoading,
  onToggleSecretaryStatus,
  showUserIdField = true,
  userIdValue = ""
}: RegisterUserSecretaryDialogProps) {
  // 表单状态
  const [userId, setUserId] = useState<string>(userIdValue);
  const [secretaryName, setSecretaryName] = useState<string>("");
  
  // 当userIdValue变化时更新本地状态
  useEffect(() => {
    if (userIdValue) {
      setUserId(userIdValue);
    }
  }, [userIdValue]);
  
  // 当对话框打开/关闭时重置表单
  useEffect(() => {
    if (!isOpen) {
      setSecretaryName("");
      if (!userIdValue) {
        setUserId("");
      }
    }
  }, [isOpen, userIdValue]);
  
  // 处理提交
  const handleSubmit = async () => {
    const success = await onSubmit(userId, secretaryName);
    if (success) {
      // 重置表单
      setSecretaryName("");
      if (!userIdValue) {
        setUserId("");
      }
    }
  };
  
  // 找到选中的秘书对象
  const selectedSecretary = secretaries.find(s => s.name === secretaryName);
  
  // 计算对话框标题
  const dialogTitle = showUserIdField 
    ? "添加新的用户-秘书关联" 
    : `为用户 "${userIdValue}" 添加秘书`;
  
  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>{dialogTitle}</DialogTitle>
        </DialogHeader>
        
        <div className="grid gap-4 py-4">
          {showUserIdField && (
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="userId" className="text-right">
                用户ID
              </Label>
              <Input
                id="userId"
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                className="col-span-3"
                placeholder="输入用户ID"
                disabled={isLoading}
                autoFocus
              />
            </div>
          )}
          
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="secretaryName" className="text-right">
              选择秘书
            </Label>
            <Select
              value={secretaryName}
              onValueChange={setSecretaryName}
              disabled={isLoading}
            >
              <SelectTrigger id="secretaryName" className="col-span-3">
                <SelectValue placeholder="选择一个秘书" />
              </SelectTrigger>
              <SelectContent>
                {secretaries.map(secretary => (
                  <SelectItem key={secretary.id} value={secretary.name}>
                    {secretary.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          
          {/* 显示选中秘书的信息 */}
          {selectedSecretary && (
            <Card className="mt-2">
              <CardContent className="pt-4">
                <div className="flex justify-between items-center">
                  <h4 className="text-base font-medium">{selectedSecretary.name}</h4>
                  <span className={cn(
                    "text-xs px-2 py-0.5 rounded-full",
                    selectedSecretary.active ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-800"
                  )}>
                    {selectedSecretary.active ? "已激活" : "未激活"}
                  </span>
                </div>
                
                <p className="text-sm text-gray-500 mt-1">ID: {selectedSecretary.id}</p>
                
                {selectedSecretary.description && (
                  <p className="text-sm mt-2">{selectedSecretary.description}</p>
                )}
                
                <div className="flex items-center mt-3">
                  <span className="text-sm mr-2">状态:</span>
                  <Switch 
                    checked={selectedSecretary.active} 
                    onCheckedChange={(checked) => onToggleSecretaryStatus(selectedSecretary.id, checked)}
                    disabled={isLoading}
                    aria-label="激活状态"
                  />
                  <span className="text-sm ml-2">
                    {selectedSecretary.active ? "已激活" : "未激活"}
                  </span>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
        
        <DialogFooter>
          <Button 
            variant="outline" 
            onClick={onClose}
            disabled={isLoading}
          >
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={isLoading || (!showUserIdField ? !secretaryName : (!userId || !secretaryName))}
          >
            {isLoading ? "添加中..." : "添加"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
} 