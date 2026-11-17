// 在一个编译单元里生成 WASAPI 所需的 GUID 定义，
// 避免 MinGW 的 uuid 库缺少音频接口 IID。
#include <initguid.h>

#include <audioclient.h>
#include <mmdeviceapi.h>
