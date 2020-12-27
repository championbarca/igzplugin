#include "JReferences.hpp"

namespace JData
{

	jobject JIgZorroBridgeObject;

	jclass JIgZorroBridgeClass;
	jclass JZorroClass;
	jclass ExceptionClass;

	JMethodDesc constructor = { nullptr, "<init>",           "()V" };
	JMethodDesc doLogin = { nullptr, "doLogin",          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I" };
	JMethodDesc doLoginV2 = { nullptr, "doLoginV2",          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I" };
	JMethodDesc doLogout = { nullptr, "doLogout",         "()I" };
	JMethodDesc doBrokerTime = { nullptr, "doBrokerTime",     "([D)I" };
	JMethodDesc doSubscribeAsset = { nullptr, "doSubscribeAsset", "(Ljava/lang/String;)I" };
	JMethodDesc doBrokerAsset = { nullptr, "doBrokerAsset",    "(Ljava/lang/String;[D)I" };
	JMethodDesc doBrokerAccount = { nullptr, "doBrokerAccount",  "([D)I" };
	JMethodDesc doBrokerBuy = { nullptr, "doBrokerBuy",      "(Ljava/lang/String;[D)I" };
	JMethodDesc doBrokerTrade = { nullptr, "doBrokerTrade",    "(I[D)I" };
	JMethodDesc doBrokerStop = { nullptr, "doBrokerStop",     "(ID)I" };
	JMethodDesc doBrokerSell = { nullptr, "doBrokerSell",     "(II)I" };
	JMethodDesc doBrokerHistory2 = { nullptr, "doBrokerHistory2", "(Ljava/lang/String;DDII[D)I" };
	JMethodDesc doSetOrderText = { nullptr, "doSetOrderText",   "(Ljava/lang/String;)I" };

	JMethodDesc excGetMessage = { nullptr, "getMessage",       "()Ljava/lang/String;" };
	JMethodDesc excGetName = { nullptr, "getName",          "()Ljava/lang/String;" };

	const JNINativeMethod nativesTable[2]{ { (char*)"jcallback_BrokerError",    (char*)"(Ljava/lang/String;)I", (void *)&jcallback_BrokerError },
	{ (char*)"jcallback_BrokerProgress", (char*)"(I)I",                  (void *)&jcallback_BrokerProgress } };

	const char* JVMClassPathOption = "-Djava.class.path=Plugin/ig/igplugin-0.32.jar";
	const char* IgZorroBridgePath = "com/danlind/igz/ZorroBridge";
	const char* ZorroPath = "com/danlind/igz/Zorro";
	const char* ExcPath = "java/lang/Class";

	const std::vector<JMethodDesc*> igZorroBridgeMethods = { &constructor,
		&doLogin,
		&doLoginV2,
		&doLogout,
		&doBrokerTime,
		&doSubscribeAsset,
		&doBrokerAsset,
		&doBrokerAccount,
		&doBrokerBuy,
		&doBrokerTrade,
		&doBrokerStop,
		&doBrokerSell,
		&doBrokerHistory2,
		&doSetOrderText };

	const int nativesTableSize = sizeof(nativesTable) / sizeof(nativesTable[0]);

	const int JNI_VERSION = JNI_VERSION_1_8;

} /* namespace JData */

