import { SecretaryInfo } from '@/types/models';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent } from '@/components/ui/card';
import { Trash2, ExternalLink } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useNavigate } from 'react-router-dom';

interface SecretaryCardProps {
  secretary: SecretaryInfo;
  userId: string;
  onToggleStatus: () => void;
  onUnregister: () => void;
}

export function SecretaryCard({ secretary, userId, onToggleStatus, onUnregister }: SecretaryCardProps) {
  const navigate = useNavigate();
  
  const handleNavigateToManager = () => {
    navigate(`/secretary_manager?id=${secretary.id}`);
  };
  
  return (
    <Card className="h-full">
      <CardContent className="pt-6">
        <div className="flex justify-between items-start mb-2">
          <h3 className="text-lg font-semibold">{secretary.name}</h3>
          <span className={cn(
            "text-xs px-2 py-1 rounded-full",
            secretary.active ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-800"
          )}>
            {secretary.active ? "已激活" : "未激活"}
          </span>
        </div>
        
        <p className="text-sm text-gray-500 mb-1">ID: {secretary.id}</p>
        
        {secretary.description && (
          <p className="text-sm my-2">{secretary.description}</p>
        )}
        
        <div className="flex items-center my-2">
          <span className="text-sm mr-2">状态:</span>
          <Switch 
            checked={secretary.active} 
            onCheckedChange={onToggleStatus}
            aria-label="激活状态"
          />
          <span className="text-sm ml-2">
            {secretary.active ? "已激活" : "未激活"}
          </span>
        </div>
        
        <div className="flex gap-2 mt-4">
          <Button 
            variant="outline" 
            size="sm"
            onClick={handleNavigateToManager}
          >
            <ExternalLink className="w-4 h-4 mr-1" />
            管理
          </Button>
          
          <Button 
            variant="outline" 
            size="sm"
            onClick={onUnregister}
          >
            <Trash2 className="w-4 h-4 mr-1" />
            解除关联
          </Button>
        </div>
      </CardContent>
    </Card>
  );
} 