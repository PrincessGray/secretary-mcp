import { SecretaryInfo } from '@/types/models';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';

interface SecretaryCardProps {
  secretary: SecretaryInfo;
  isSelected: boolean;
  onSelect: () => void;
  onToggleStatus: (active: boolean) => void;
  onDelete: () => void;
}

export function SecretaryCard({ 
  secretary, 
  isSelected, 
  onSelect, 
  onToggleStatus, 
  onDelete 
}: SecretaryCardProps) {
  return (
    <div 
      className={cn(
        "p-4 border rounded-lg mb-2 cursor-pointer transition-colors",
        isSelected ? "bg-muted " : "hover:bg-muted/50"
      )}
      onClick={onSelect}
    >
      <div className="flex justify-between items-start">
        <div className="flex-1">
          <h3 className="font-medium">{secretary.name}</h3>
          {secretary.description && (
            <p className="text-sm text-muted-foreground mt-1">{secretary.description}</p>
          )}
        </div>
        
        <div className="flex items-center space-x-2" onClick={e => e.stopPropagation()}>
          <div className="flex items-center space-x-2">
            <Switch 
              checked={secretary.active} 
              onCheckedChange={onToggleStatus}
              id={`secretary-switch-${secretary.id}`}
            />
            <Label htmlFor={`secretary-switch-${secretary.id}`} className="text-xs">
              {secretary.active ? '已激活' : '未激活'}
            </Label>
          </div>
          
          <Button 
            variant="ghost" 
            size="icon" 
            className="text-destructive hover:text-destructive hover:bg-destructive/10"
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}
