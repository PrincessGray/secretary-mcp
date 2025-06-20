import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Calendar, Clock, Download, Filter, RefreshCw, Search, User } from "lucide-react"
import axios from "axios" // 确保已安装axios

type LogLevel = "INFO" | "WARNING" | "ERROR" | "DEBUG"
type LogEntry = {
  id: number
  timestamp: string
  level: LogLevel
  message: string
  source: string
  user?: string
  details?: string
}

export default function LogViewer() {
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [searchQuery, setSearchQuery] = useState("")
  const [levelFilter, setLevelFilter] = useState<LogLevel | "ALL">("ALL")
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  
  useEffect(() => {
    fetchLogs()
  }, [])
  
  const fetchLogs = async () => {
    setIsLoading(true)
    setError(null)
    
    try {
      const response = await axios.get('/api/logs')
      setLogs(response.data)
    } catch (err) {
      console.error('获取日志失败:', err)
      setError('获取日志数据失败，请稍后重试')
      // 保留旧数据或清空
      // setLogs([])
    } finally {
      setIsLoading(false)
    }
  }
  
  const exportLogs = async () => {
    try {
      const response = await axios.get('/api/logs/export', {
        responseType: 'blob'
      })
      
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `logs-${new Date().toISOString().split('T')[0]}.csv`)
      document.body.appendChild(link)
      link.click()
      link.remove()
    } catch (err) {
      console.error('导出日志失败:', err)
      setError('导出日志失败，请稍后重试')
    }
  }
  
  const getLevelColor = (level: LogLevel) => {
    switch(level) {
      case "INFO": return "bg-blue-100 text-blue-800"
      case "WARNING": return "bg-yellow-100 text-yellow-800"
      case "ERROR": return "bg-red-100 text-red-800"
      case "DEBUG": return "bg-gray-100 text-gray-800"
      default: return "bg-gray-100 text-gray-800"
    }
  }
  
  const filteredLogs = logs.filter(log => {
    const matchesSearch = 
      log.message.toLowerCase().includes(searchQuery.toLowerCase()) ||
      log.source.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (log.user && log.user.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (log.details && log.details.toLowerCase().includes(searchQuery.toLowerCase()))
    
    const matchesLevel = levelFilter === "ALL" || log.level === levelFilter
    
    return matchesSearch && matchesLevel
  })
  
  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">日志查看</h1>
        <div className="flex gap-2">
          <Button variant="outline" className="gap-2" onClick={fetchLogs} disabled={isLoading}>
            <RefreshCw className={`h-4 w-4 ${isLoading ? "animate-spin" : ""}`} />
            刷新
          </Button>
          <Button variant="outline" className="gap-2" onClick={exportLogs} disabled={isLoading}>
            <Download className="h-4 w-4" />
            导出
          </Button>
        </div>
      </div>
      
      <div className="flex flex-col md:flex-row gap-4 mb-6">
        <div className="relative flex-1">
          <div className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none">
            <Search className="h-4 w-4 text-muted-foreground" />
          </div>
          <input
            type="text"
            placeholder="搜索日志内容..."
            className="w-full pl-10 py-2 border rounded-md bg-background"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        
        <div className="relative">
          <div className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none">
            <Filter className="h-4 w-4 text-muted-foreground" />
          </div>
          <select
            className="pl-10 py-2 border rounded-md bg-background"
            value={levelFilter}
            onChange={(e) => setLevelFilter(e.target.value as LogLevel | "ALL")}
          >
            <option value="ALL">所有级别</option>
            <option value="INFO">信息</option>
            <option value="WARNING">警告</option>
            <option value="ERROR">错误</option>
            <option value="DEBUG">调试</option>
          </select>
        </div>
      </div>
      
      {error && (
        <div className="p-4 mb-4 bg-red-50 border border-red-200 text-red-700 rounded-md">
          {error}
        </div>
      )}
      
      <div className="border rounded-lg">
        {isLoading ? (
          <div className="p-8 text-center">
            <RefreshCw className="h-8 w-8 animate-spin mx-auto mb-4 text-muted-foreground" />
            <p className="text-muted-foreground">加载日志数据...</p>
          </div>
        ) : filteredLogs.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">
            没有符合条件的日志数据
          </div>
        ) : (
          <div className="divide-y">
            {filteredLogs.map((log) => (
              <div key={log.id} className="p-4 hover:bg-muted/50">
                <div className="flex flex-wrap justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${getLevelColor(log.level)}`}>
                      {log.level}
                    </span>
                    <span className="text-sm font-medium">{log.source}</span>
                  </div>
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    {log.user && (
                      <div className="flex items-center gap-1">
                        <User className="h-3 w-3" />
                        <span>{log.user}</span>
                      </div>
                    )}
                    <div className="flex items-center gap-1">
                      <Calendar className="h-3 w-3" />
                      <span>{log.timestamp.split(' ')[0]}</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      <span>{log.timestamp.split(' ')[1]}</span>
                    </div>
                  </div>
                </div>
                <p className="text-sm mb-1">{log.message}</p>
                {log.details && (
                  <pre className="mt-2 p-2 bg-muted rounded text-xs overflow-x-auto">
                    {log.details}
                  </pre>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
} 