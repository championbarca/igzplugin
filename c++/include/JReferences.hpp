#ifndef JREFERENCES_HPP
#define JREFERENCES_HPP

#include <vector>
#include "igzorrobridge.hpp"

typedef struct JMethodDesc
{
    jmethodID methodID;
    const char *name;
    const char *signature;
} JMethodDesc;

namespace JData
{

extern jobject JIgZorroBridgeObject;

extern jclass JIgZorroBridgeClass;
extern jclass JZorroClass;
extern jclass ExceptionClass;

extern JMethodDesc constructor;
extern JMethodDesc doLogin;
extern JMethodDesc doLoginV2;
extern JMethodDesc doLogout;
extern JMethodDesc doBrokerTime;
extern JMethodDesc doSubscribeAsset;
extern JMethodDesc doBrokerAsset;
extern JMethodDesc doBrokerAccount;
extern JMethodDesc doBrokerBuy;
extern JMethodDesc doBrokerTrade;
extern JMethodDesc doBrokerStop;
extern JMethodDesc doBrokerSell;
extern JMethodDesc doBrokerHistory2;
extern JMethodDesc doSetOrderText;

extern JMethodDesc excGetMessage;
extern JMethodDesc excGetName;

extern const JNINativeMethod nativesTable[2];
extern const int nativesTableSize;

extern const char* JVMClassPathOption;
extern const char* IgZorroBridgePath;
extern const char* ZorroPath;
extern const char* ExcPath;

extern const std::vector<JMethodDesc*> igZorroBridgeMethods;

extern const int JNI_VERSION;

} /* namespace JData */

#endif /* JREFERENCES_HPP */
