import { Card, CardContent } from "@/components/ui/card";

interface ApiResponseViewerProps {
  response: string;
  requestLog?: string;
}

export function ApiResponseViewer({ response, requestLog }: ApiResponseViewerProps) {
  return (
    <Card>
      <CardContent className="p-6">
        <h3 className="text-xl font-semibold mb-4">API响应</h3>
        
        {requestLog && (
          <div className="mb-4 text-muted-foreground text-sm">
            {requestLog}
          </div>
        )}
        
        <div className="bg-muted p-4 rounded-md overflow-auto max-h-[500px] border">
          <pre className="text-sm font-mono">
            {response || "等待API响应..."}
          </pre>
        </div>
      </CardContent>
    </Card>
  );
} 