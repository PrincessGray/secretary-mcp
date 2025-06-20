import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusCircleIcon, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SecretaryCard } from './SecretaryCard';
import { RegisterUserSecretaryDialog } from './RegisterUserSecretaryDialog';
import { useUserSecretaryMappings } from './hooks/useUserSecretaryMappings';

export default function UserSecretaryMapping() {
  const navigate = useNavigate();
  
  // 使用自定义Hook管理用户-秘书映射
  const {
    mappings,
    secretaries,
    selectedUserId,
    setSelectedUserId,
    isLoading,
    registerUserSecretary,
    unregisterUserSecretary,
    unregisterAllUserSecretaries,
    toggleSecretaryStatus,
    refreshData
  } = useUserSecretaryMappings();
  
  // 对话框状态
  const [registerDialogOpen, setRegisterDialogOpen] = useState(false);
  const [addSecretaryDialogOpen, setAddSecretaryDialogOpen] = useState(false);
  const [currentUserId, setCurrentUserId] = useState("");
  
  // 打开添加秘书对话框
  const openAddSecretaryDialog = (userId: string) => {
    setCurrentUserId(userId);
    setAddSecretaryDialogOpen(true);
  };
  
  // 导航到秘书管理界面
  const navigateToSecretaryManager = (secretaryName: string) => {
    const secretary = secretaries.find(s => s.name === secretaryName);
    if (secretary) {
      navigate(`/secretary-manager?secretaryId=${secretary.id}`);
    }
  };
  
  // 获取用户ID列表
  const userIds = Object.keys(mappings);
  
  return (
    <div className="container mx-auto py-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">用户-秘书对应关系</h1>
        
        <div className="flex gap-2">
          <Button 
            onClick={() => setRegisterDialogOpen(true)}
            disabled={isLoading}
          >
            <PlusCircleIcon className="w-4 h-4 mr-1" />
            添加新关联
          </Button>
          
          <Button 
            variant="outline" 
            onClick={refreshData}
            disabled={isLoading}
          >
            <RefreshCw className={`w-4 h-4 mr-1 ${isLoading ? 'animate-spin' : ''}`} />
            刷新数据
          </Button>
        </div>
      </div>
      
      {userIds.length === 0 ? (
        <div className="rounded-lg border p-8 text-center">
          {isLoading ? '加载中...' : '暂无用户-秘书关联数据'}
        </div>
      ) : (
        <div className="grid grid-cols-4 gap-4">
          {/* 左侧用户列表 */}
          <div className="col-span-1">
            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold mb-4">用户列表</h2>
              <div className="divide-y">
                {userIds.map(userId => (
                  <div 
                    key={userId}
                    className={`py-2 px-3 cursor-pointer flex justify-between items-center ${selectedUserId === userId ? 'bg-muted rounded' : ''}`}
                    onClick={() => setSelectedUserId(userId)}
                  >
                    <span>{userId}</span>
                    <button
                      className="p-1 rounded-full hover:bg-gray-200"
                      onClick={(e) => {
                        e.stopPropagation();
                        openAddSecretaryDialog(userId);
                      }}
                      title="为此用户添加秘书"
                    >
                      <PlusCircleIcon className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </div>
          
          {/* 右侧秘书详情 */}
          <div className="col-span-3">
            <div className="rounded-lg border p-4 h-full">
              {selectedUserId ? (
                <>
                  <div className="flex justify-between items-center mb-4">
                    <h2 className="text-lg font-semibold">
                      用户 "{selectedUserId}" 的秘书关联
                    </h2>
                    
                    <div className="flex gap-2">
                      <Button 
                        variant="outline"
                        size="sm"
                        onClick={() => openAddSecretaryDialog(selectedUserId)}
                        disabled={isLoading}
                      >
                        <PlusCircleIcon className="w-4 h-4 mr-1" />
                        添加秘书
                      </Button>
                      
                      <Button 
                        variant="outline" 
                        size="sm"
                        onClick={() => unregisterAllUserSecretaries(selectedUserId)}
                        disabled={isLoading}
                        className="text-red-500 hover:text-red-700"
                      >
                        解除所有关联
                      </Button>
                    </div>
                  </div>
                  
                  <div className="border-t pt-4">
                    {(() => {
                      const secretaryList = mappings[selectedUserId] || [];
                      
                      if (secretaryList.length === 0) {
                        return (
                          <div className="text-center py-8 text-gray-500">
                            此用户暂未关联任何秘书
                          </div>
                        );
                      }
                      
                      return (
                        <div className="grid grid-cols-2 gap-4">
                          {secretaryList.map((secretaryName, index) => {
                            const secretary = secretaries.find(s => s.name === secretaryName);
                            return secretary ? (
                              <SecretaryCard
                                key={`${selectedUserId}-${secretary.id}-${index}`}
                                secretary={secretary}
                                userId={selectedUserId}
                                onToggleStatus={() => toggleSecretaryStatus(secretary.id, !secretary.active)}
                                onUnregister={() => unregisterUserSecretary(selectedUserId, secretary.name)}
                                onNavigateToManager={() => navigateToSecretaryManager(secretary.name)}
                              />
                            ) : null;
                          })}
                        </div>
                      );
                    })()}
                  </div>
                </>
              ) : (
                <div className="flex items-center justify-center h-full text-gray-500">
                  请从左侧选择一个用户查看其关联的秘书
                </div>
              )}
            </div>
          </div>
        </div>
      )}
      
      {/* 注册新用户-秘书关联对话框 */}
      <RegisterUserSecretaryDialog
        isOpen={registerDialogOpen}
        onClose={() => setRegisterDialogOpen(false)}
        onSubmit={registerUserSecretary}
        secretaries={secretaries}
        isLoading={isLoading}
        onToggleSecretaryStatus={toggleSecretaryStatus}
        showUserIdField={true}
      />
      
      {/* 为特定用户添加秘书对话框 */}
      <RegisterUserSecretaryDialog
        isOpen={addSecretaryDialogOpen}
        onClose={() => setAddSecretaryDialogOpen(false)}
        onSubmit={(_, secretaryName) => registerUserSecretary(currentUserId, secretaryName)}
        secretaries={secretaries}
        isLoading={isLoading}
        onToggleSecretaryStatus={toggleSecretaryStatus}
        showUserIdField={false}
        userIdValue={currentUserId}
      />
    </div>
  );
} 