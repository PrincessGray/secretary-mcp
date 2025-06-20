import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Eye, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
interface Template {
  id: string;
  name: string;
  description: string;
  connectionType: string;
  createdAt: string;
  updatedAt?: string;
}

interface TemplateListProps {
  templates: Template[];
  onView: (id: string) => void;
  onDelete: (id: string) => void;
}

export function TemplateList({ templates, onView, onDelete }: TemplateListProps) {
  if (!templates || templates.length === 0) {
    return (
      <div className="text-muted-foreground text-center py-8">
        没有找到模板，请点击"刷新数据"或"新建模板"按钮。
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {templates.map((template) => (
        <Card key={template.id} className="overflow-hidden">
          <CardContent className="p-6">
            <div className="flex flex-col space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold flex-shrink-0 mr-4">{template.name}</h3>
                
                <div className="flex items-center gap-4 flex-grow justify-start text-sm text-muted-foreground">
                <Badge>{template.connectionType}</Badge>
                  
                  {template.connectionType?.toLowerCase() === 'stdio' && (
                    <Badge>{template.connectionProfile?.managementType || "NPX"}</Badge>
                  )}
                </div>
                
                <div className="flex space-x-2 flex-shrink-0">
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => onView(template.id)}
                  >
                    <Eye className="mr-1 h-4 w-4" />
                    查看
                  </Button>
                  <Button 
                    variant="destructive" 
                    size="sm"
                    onClick={() => onDelete(template.id)}
                  >
                    <Trash2 className="mr-1 h-4 w-4" />
                    删除
                  </Button>
                </div>
              </div>
              
              <p className="text-sm text-muted-foreground">
                {template.description || "无描述"}
              </p>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
} 