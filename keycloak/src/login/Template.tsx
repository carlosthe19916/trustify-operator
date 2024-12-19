import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import { useInitialize } from "keycloakify/login/Template.useInitialize";
import type { TemplateProps } from "keycloakify/login/TemplateProps";
import { clsx } from "keycloakify/tools/clsx";
import { useSetClassName } from "keycloakify/tools/useSetClassName";
import { useEffect, useState } from "react";
import type { I18n } from "./i18n";
import type { KcContext } from "./KcContext";

import {
    Alert,
    ListItem,
    ListVariant,
    LoginFooterItem,
    LoginPage,
    MenuToggle,
    MenuToggleElement,
    Select,
    SelectList,
    SelectOption,
    Switch,
    Tooltip
} from "@patternfly/react-core";

import "@patternfly/react-core/dist/styles/base.css";
import "./assets/styles/login.css";

import backgroundImg2 from "./assets/images/pf-background.svg";
import brandImg from "./assets/images/masthead-logo.svg";
import brandImgForDark from "./assets/images/masthead-logo-for-dark.svg";

export default function Template(props: TemplateProps<KcContext, I18n>) {
    const [isHeaderUtilsOpen, setIsHeaderUtilsOpen] = useState(false);
    const [isDarkTheme, setIsDarkTheme] = useState(localStorage.getItem("isDarkTheme") === "true" ? true : false);

    useEffect(() => {
        if (isDarkTheme) {
            document.documentElement.classList.add("pf-v6-theme-dark");
            localStorage.setItem("isDarkTheme", "true");
        } else {
            document.documentElement.classList.remove("pf-v6-theme-dark");
            localStorage.setItem("isDarkTheme", "false");
        }
    }, [isDarkTheme]);

    //

    const {
        // displayInfo = false,
        displayMessage = true,
        displayRequiredFields = false,
        // headerNode,
        socialProvidersNode = null,
        infoNode = null,
        documentTitle,
        bodyClassName,
        kcContext,
        i18n,
        doUseDefaultCss,
        classes,
        children
    } = props;

    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { msg, msgStr, currentLanguage, enabledLanguages } = i18n;

    const { /*realm,*/ auth, url, message, isAppInitiatedAction } = kcContext;

    useEffect(() => {
        document.title = documentTitle ?? msgStr("loginTitle", kcContext.realm.displayName);
    }, []);

    useSetClassName({
        qualifiedName: "html",
        className: kcClsx("kcHtmlClass")
    });

    useSetClassName({
        qualifiedName: "body",
        className: bodyClassName ?? kcClsx("kcBodyClass")
    });

    const { isReadyToRender } = useInitialize({ kcContext, doUseDefaultCss });

    if (!isReadyToRender) {
        return null;
    }

    //

    const headerUtilsOptions = (
        <SelectList>
            {enabledLanguages.map(({ languageTag, label, href }, i) => (
                <SelectOption key={languageTag} id={`language-${i + 1}`} component={"a"} to={href}>
                    {label}
                </SelectOption>
            ))}
        </SelectList>
    );

    const headerUtils = (
        <Select
            aria-label={msgStr("languages")}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle ref={toggleRef} onClick={() => setIsHeaderUtilsOpen(!isHeaderUtilsOpen)} isExpanded={isHeaderUtilsOpen}>
                    {currentLanguage.label}
                </MenuToggle>
            )}
            onOpenChange={isOpen => setIsHeaderUtilsOpen(isOpen)}
            selected={currentLanguage.label}
            isOpen={isHeaderUtilsOpen}
        >
            {headerUtilsOptions}
        </Select>
    );

    //

    const signUpForAccountMessage = <>{infoNode}</>;

    const listItem = (
        <>
            <ListItem>
                <LoginFooterItem href="https://trustification.io/">Documentation</LoginFooterItem>
            </ListItem>
            <ListItem>
                <LoginFooterItem href="https://app.element.io/?updated=1.11.32#/room/#trustification:matrix.org">Chat with us</LoginFooterItem>
            </ListItem>
            <ListItem>
                <LoginFooterItem href="https://www.trustification.io/blog">Blog</LoginFooterItem>
            </ListItem>
        </>
    );

    return (
        <>
            <LoginPage
                footerListVariants={ListVariant.inline}
                brandImgSrc={!isDarkTheme ? brandImg : brandImgForDark}
                brandImgAlt="App logo"
                backgroundImgSrc={backgroundImg2}
                footerListItems={listItem}
                textContent="A community, vendor-neutral, thought-leadering, mostly informational collection of resources devoted to making Software Supply Chains easier to create, manage, consume and ultimately… to trust!"
                loginTitle="Sign in to your account"
                headerUtilities={enabledLanguages.length > 1 ? headerUtils : undefined}
                socialMediaLoginContent={socialProvidersNode}
                socialMediaLoginAriaLabel="Log in with social media"
                signUpForAccountMessage={signUpForAccountMessage}
            >
                {(() => {
                    const node = !(auth !== undefined && auth.showUsername && !auth.showResetCredentials) ? (
                        <></>
                    ) : (
                        <div id="kc-username" className={kcClsx("kcFormGroupClass")}>
                            <label id="kc-attempted-username">{auth.attemptedUsername}</label>
                            <a id="reset-login" href={url.loginRestartFlowUrl} aria-label={msgStr("restartLoginTooltip")}>
                                <Tooltip
                                    content={
                                        <div>
                                            <span className="kc-tooltip-text">{msg("restartLoginTooltip")}</span>
                                        </div>
                                    }
                                >
                                    <i className={kcClsx("kcResetFlowIcon")}></i>
                                </Tooltip>
                            </a>
                        </div>
                    );

                    if (displayRequiredFields) {
                        return (
                            <div className={kcClsx("kcContentWrapperClass")}>
                                <div className={clsx(kcClsx("kcLabelWrapperClass"), "subtitle")}>
                                    <span className="subtitle">
                                        <span className="required">*</span>
                                        {msg("requiredFields")}
                                    </span>
                                </div>
                                <div className="col-md-10">{node}</div>
                            </div>
                        );
                    }

                    return node;
                })()}
                {displayMessage && message !== undefined && (message.type !== "warning" || !isAppInitiatedAction) && (
                    <>
                        <Alert
                            variant={message?.type === "error" ? "danger" : message.type}
                            title={
                                <span
                                    className={kcClsx("kcAlertTitleClass")}
                                    dangerouslySetInnerHTML={{
                                        __html: kcSanitize(message.summary)
                                    }}
                                />
                            }
                            ouiaId="DangerAlert"
                        />
                    </>
                )}
                {children}
                {auth !== undefined && auth.showTryAnotherWayLink && (
                    <form id="kc-select-try-another-way-form" action={url.loginAction} method="post">
                        <div className={kcClsx("kcFormGroupClass")}>
                            <input type="hidden" name="tryAnotherWay" value="on" />
                            <a
                                href="#"
                                id="try-another-way"
                                onClick={() => {
                                    document.forms["kc-select-try-another-way-form" as never].submit();
                                    return false;
                                }}
                            >
                                {msg("doTryAnotherWay")}
                            </a>
                        </div>
                    </form>
                )}
            </LoginPage>

            <div className="pf-v6-l-flex pf-m-column pf-m-gap-lg ws-full-page-utils pf-v6-m-dir-ltr ">
                <Switch
                    id="theme-switch"
                    label="Dark theme"
                    isChecked={isDarkTheme}
                    onChange={(_e, checked) => setIsDarkTheme(checked)}
                    ouiaId="ThemeSwitch"
                />
            </div>
        </>
    );
}
