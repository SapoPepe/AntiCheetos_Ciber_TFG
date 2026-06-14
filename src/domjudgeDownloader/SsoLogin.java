package domjudgeDownloader;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SsoLogin {

    public static String[] getCookies(String baseURL) {
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        System.setProperty("webdriver.chrome.silentOutput", "true");
        WebDriver driver = new ChromeDriver();

        try {
            System.out.println("[*] Opening browser for login");
            String url = baseURL.endsWith("/") ? baseURL.substring(0, baseURL.length()-1) : baseURL;
            driver.get(url + "/login");
            System.out.println("[*] Complete login in the browser");

            String oauthProxy = null;
            String phpSessId = null;


            while (true) {
                Thread.sleep(1000); // Check of status each 1 second

                Cookie proxyCookie = driver.manage().getCookieNamed("_oauth2_proxy");
                Cookie phpCookie = driver.manage().getCookieNamed("PHPSESSID");

                String currentUrl = driver.getCurrentUrl();

                // We are not in login page and cookies have values
                if (!currentUrl.contains("/login") && !currentUrl.contains("oauth2") && proxyCookie != null && phpCookie != null) {
                    Thread.sleep(1000);
                    oauthProxy = proxyCookie.getValue();
                    phpSessId = phpCookie.getValue();
                    break;
                }

                if (driver.getWindowHandles().isEmpty()) {
                    System.out.println("[ERROR] Browser closed before login. Can no continue");
                    return null;
                }
            }

            System.out.println("[*] Login done");
            return new String[]{oauthProxy, phpSessId};

        } catch (Exception e) {
            System.out.println("[ERROR] Error while login");
            return null;
        } finally {
            driver.quit();
        }
    }
}