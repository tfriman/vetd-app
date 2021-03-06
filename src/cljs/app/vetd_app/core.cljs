(ns vetd-app.core
  (:require vetd-app.websockets
            vetd-app.graphql
            [vetd-app.local-store :as local-store]
            vetd-app.cookies
            vetd-app.url
            vetd-app.analytics
            vetd-app.debounce
            vetd-app.sub-trackers
            vetd-app.common.fx
            vetd-app.orgs.fx
            vetd-app.groups.fx
            [vetd-app.websockets :as ws]
            [vetd-app.hooks :as hooks]
            [vetd-app.buyers.fixtures :as b-fix]
            [vetd-app.buyers.pages.search :as p-bsearch]
            [vetd-app.buyers.pages.product-detail :as p-bproduct-detail]
            [vetd-app.buyers.pages.preposals :as p-bpreposals]
            [vetd-app.buyers.pages.rounds :as p-brounds]
            [vetd-app.buyers.pages.round-detail.index :as p-bround-detail]
            [vetd-app.buyers.pages.stack :as p-bstack]
            [vetd-app.buyers.pages.stack-detail :as p-bstack-detail]
            [vetd-app.vendors.fixtures :as v-fix]
            [vetd-app.vendors.pages.preposals :as p-vpreposals]
            [vetd-app.vendors.pages.preposal-detail :as p-vpreposal-detail]
            [vetd-app.vendors.pages.products :as p-vprods]
            [vetd-app.vendors.pages.product-detail :as p-vprod-detail]
            [vetd-app.vendors.pages.profile :as p-vprofile]
            [vetd-app.vendors.pages.rounds :as p-vrounds]
            [vetd-app.vendors.pages.round-product-detail :as p-vround-product-detail]
            [vetd-app.groups.pages.home :as p-ghome]
            [vetd-app.groups.pages.settings :as p-gsettings]
            [vetd-app.groups.pages.detail :as p-gdetail]
            [vetd-app.common.components :as cc]
            [vetd-app.common.fixtures :as pub-fix]
            [vetd-app.common.pages.signup :as p-signup]
            [vetd-app.common.pages.join-org-signup :as p-join-org-signup]
            [vetd-app.common.pages.login :as p-login]
            [vetd-app.common.pages.forgot-password :as p-forgot-password]
            [vetd-app.common.pages.settings :as p-settings]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [secretary.core :as sec]
            [accountant.core :as acct]
            [clerk.core :as clerk]
            [clojure.string :as s]))

(println "START core")

(hooks/reg-hooks! hooks/c-page
                  {:login #'p-login/c-page
                   :signup #'p-signup/c-page
                   :join-org-signup #'p-join-org-signup/c-page
                   :forgot-password #'p-forgot-password/c-page
                   :b/settings #'p-settings/c-page
                   :v/settings #'p-settings/c-page
                   :b/search #'p-bsearch/c-page
                   :b/product-detail #'p-bproduct-detail/c-page
                   :b/preposals #'p-bpreposals/c-page
                   :b/rounds #'p-brounds/c-page
                   :b/round-detail #'p-bround-detail/c-page
                   :b/stack #'p-bstack/c-page
                   :b/stack-detail #'p-bstack-detail/c-page
                   :v/preposals #'p-vpreposals/c-page
                   :v/preposal-detail #'p-vpreposal-detail/c-page
                   :v/products #'p-vprods/c-page
                   :v/product-detail #'p-vprod-detail/c-page
                   :v/profile #'p-vprofile/c-page
                   :v/rounds #'p-vrounds/c-page
                   :v/round-product-detail #'p-vround-product-detail/c-page
                   :g/home #'p-ghome/c-page
                   :g/settings #'p-gsettings/c-page
                   :g/detail #'p-gdetail/c-page})

(hooks/reg-hooks! hooks/c-container
                  {:login #'pub-fix/container
                   :signup #'pub-fix/container
                   :join-org-signup #'pub-fix/container
                   :forgot-password #'pub-fix/container
                   :b/settings #'b-fix/container
                   :v/settings #'v-fix/container
                   :b/search #'b-fix/container
                   :b/product-detail #'b-fix/container
                   :b/preposals #'b-fix/container
                   :b/rounds #'b-fix/container
                   :b/round-detail #'b-fix/appendable-container
                   :b/stack #'b-fix/container
                   :b/stack-detail #'b-fix/container
                   :g/home #'b-fix/container
                   :g/settings #'b-fix/container
                   :g/detail #'b-fix/container
                   :v/preposals #'v-fix/container
                   :v/preposal-detail #'v-fix/container
                   :v/products #'v-fix/container
                   :v/product-detail #'v-fix/container
                   :v/profile #'v-fix/container
                   :v/rounds #'v-fix/container
                   :v/round-product-detail #'v-fix/container})

(rf/reg-event-db
 :init-db
 (constantly
  {:search p-bsearch/init-db
   :groups p-ghome/init-db
   :stack p-bstack/init-db
   :round p-bround-detail/init-db
   ;; stores refs by keywords, that can be used with :scroll-to fx
   :scroll-to-refs {}
   ;; any events put in here will be dispatched when [:dispatch-stash.pop]
   :dispatch-stash {}
   :preposals-filter p-bpreposals/default-preposals-filter
   :rounds-filter {:selected-statuses #{}}
   ;; it think this for within the round grid, not sure if it's currently being used
   ;; in fact, I'm almost certain it's not being used
   ;; entities (by ID) that are in a loading?=true state (for UI display)
   :loading? {:products #{}}
   ;; a multi-purpose modal that can be used via event dispatch
   ;; see event :modal
   :modal {:showing?& (r/atom false)}
   ;; only takes effect if the OS also is set to "Dark Mode"
   :dark-mode? (= (local-store/get-item :dark-mode?) "true")}))

;; Unless a path is listed here, non-sessioned users will be
;; redirected to /login when they visit that path.
(def public-routes #{""
                     "/"
                     "/chrome-extension-installed"
                     "/login"
                     "/signup/:type"
                     "/signup-by-invite/:link-key"
                     "/forgot-password/"
                     "/forgot-password/:email-address"
                     "/l/:link-key"})

(rf/reg-sub
 :page
 (fn [{:keys [page]}] page))

(rf/reg-sub
 :page-params
 (fn [{:keys [page-params]}] page-params))

(rf/reg-sub
 :active-org
 (fn [{:keys [active-memb-id memberships]} _]
   (->> memberships
        (filter #(-> % :id (= active-memb-id)))
        first
        :org)))

(rf/reg-sub
 :org-id
 :<- [:active-org] 
 (fn [{:keys [id]}] id))

(rf/reg-sub
 :org-name
 :<- [:active-org] 
 (fn [{:keys [oname]}] oname))

(rf/reg-sub
 :vendor?
 :<- [:active-org] 
 (fn [{:keys [vendor?]}] vendor?))

(rf/reg-sub
 :group-ids
 :<- [:active-org] 
 (fn [{:keys [groups]}]
   (mapv :id groups)))

(rf/reg-sub
 :user
 (fn [{:keys [user]}] user))

(rf/reg-sub
 :user-id
 :<- [:user] 
 (fn [{:keys [id]}] id))

(rf/reg-sub
 :user-name
 :<- [:user] 
 (fn [{:keys [uname]}] uname))

(rf/reg-sub
 :user-email
 :<- [:user] 
 (fn [{:keys [email]}] email))

(rf/reg-sub
 :admin-of-groups
 (fn [{:keys [admin-of-groups]}] admin-of-groups))

;; Vetd admin
(rf/reg-sub
 :admin?
 (fn [{:keys [admin?]}] admin?))

(rf/reg-event-fx
 :do-fx
 (fn [_ [_ fx]]
   fx))

(rf/reg-event-fx
 :nav-home
 (fn [{:keys [db]} [_ first-session? chrome-extension-installed?]]
   (let [{:keys [memberships active-memb-id admin?]} db
         active-org (some->> memberships
                             (filter #(-> % :id (= active-memb-id)))
                             first
                             :org)]
     (merge {:nav (if admin?
                    {:path "/a/search"}
                    ;; TODO support multiple orgs
                    (if active-org
                      (if (:buyer? active-org)
                        (if first-session?
                          {:path "/b/stack"}
                          (if (seq (:groups active-org)) ;; in a community?
                            {:path "/c/home"}
                            {:path "/b/stack"}))
                        {:path "/v/estimates"})
                      {:path "/login"}))}
            (when chrome-extension-installed? ;; was just now installed
              (when-not active-org
                {:nav {:path "/signup/buyer"}
                 :db (assoc db
                            :info-message
                            {:header "Chrome Extension Installed"
                             :content (r/as-element
                                       [:<>
                                        "Please sign up for a Vetd account, or "
                                        [:a {:on-click #(rf/dispatch [:nav-login])} "log in"]
                                        " to connect an existing account."])})}))))))

(defn c-page []
  (let [page& (rf/subscribe [:page])
        modal& (rf/subscribe [:modal])]
    (fn []
      [:div#page
       [(hooks/c-container :admin-overlay)
        [(hooks/c-admin @page&)]]
       [(hooks/c-container @page&)
        [(hooks/c-page @page&)]]
       [cc/c-modal @modal&]])))

(defn mount-components []
  (.log js/console "mount-components STARTED")
  (r/render [c-page] (.getElementById js/document "app"))
  (.log js/console "mount-components DONE"))

(defn mount-components-dev []
  (.log js/console "mount-components-dev STARTED")
  (rf/dispatch-sync [:dispose-sub-trackers])
  (rf/clear-subscription-cache!)
  (rf/dispatch-sync [:reg-sub-trackers])
  (mount-components)
  (.log js/console "mount-components-dev DONE"))

;;;; Routes

;; Common
(sec/defroute blank-path "" []
  (rf/dispatch [:nav-home]))

(sec/defroute home-path "/" []
  (rf/dispatch [:nav-home]))

(sec/defroute home-after-chrome-extension-installed "/chrome-extension-installed" []
  (rf/dispatch [:nav-home false true]))

(sec/defroute login-path "/login" []
  (rf/dispatch [:route-login]))

(sec/defroute signup-path "/signup/:type" [type]
  (rf/dispatch [:route-signup type]))

(sec/defroute join-org-signup-path "/signup-by-invite/:link-key" [link-key]
  (rf/dispatch [:route-join-org-signup link-key]))

(sec/defroute forgot-password-path "/forgot-password/" []
  (rf/dispatch [:route-forgot-password]))
(sec/defroute forgot-password-prefill-path "/forgot-password/:email-address" [email-address]
  (rf/dispatch [:route-forgot-password email-address]))

(sec/defroute buyers-settings "/b/settings" []
  (rf/dispatch [:route-settings false]))
(sec/defroute vendors-settings "/v/settings" []
  (rf/dispatch [:route-settings true]))

(sec/defroute groups-path "/c/:path-or-idstr" [path-or-idstr]
  (rf/dispatch (case path-or-idstr
                 "home" [:g/route-home]
                 "settings" [:g/route-settings]
                 [:g/route-detail path-or-idstr])))

;; Link - special links for actions such as reset password, or account verification
(sec/defroute link-path "/l/:link-key" [link-key]
  (rf/dispatch [:read-link link-key]))

;; Buyers
(sec/defroute buyers-search-root "/b/search" []
  (rf/dispatch [:b/route-search]))
(sec/defroute buyers-search "/b/search/:search-term" [search-term]
  (rf/dispatch [:b/route-search search-term]))

(sec/defroute buyers-product-detail "/b/products/:idstr" [idstr]
  (rf/dispatch [:b/route-product-detail idstr]))

(sec/defroute buyers-preposals "/b/estimates" [query-params]
  (rf/dispatch [:b/route-preposals query-params]))

(sec/defroute buyers-rounds "/b/rounds" [query-params]
  (rf/dispatch [:b/route-rounds query-params]))
(sec/defroute buyers-round-detail "/b/rounds/:idstr" [idstr]
  (rf/dispatch [:b/route-round-detail idstr]))

;; edit your own stack
(sec/defroute buyers-stack "/b/stack" []
  (rf/dispatch [:b/route-stack]))
(sec/defroute buyers-stack-with-param "/b/stack/:param" [param]
  (rf/dispatch [:b/route-stack param]))
;; view another org's stack (idstr of the org you want to view)
(sec/defroute buyers-stack-detail "/b/stacks/:idstr" [idstr]
  (rf/dispatch [:b/route-stack-detail idstr]))

;; Vendors
(sec/defroute vendors-preposals "/v/estimates" [query-params]
  (rf/dispatch [:v/route-preposals query-params]))

(sec/defroute vendors-preposal-detail "/v/estimates/:idstr" [idstr]
  (rf/dispatch [:v/route-preposal-detail idstr]))

(sec/defroute vendors-products "/v/products" [query-params]
  (rf/dispatch [:v/route-products query-params]))

(sec/defroute vendors-product-detail "/v/products/:idstr" [idstr]
  (rf/dispatch [:v/route-product-detail idstr]))

(sec/defroute vendors-profile "/v/profile" [query-params]
  (rf/dispatch [:v/route-profile query-params]))

(sec/defroute vendors-rounds-path "/v/rounds" [query-params]
  (rf/dispatch [:v/route-rounds query-params]))

(sec/defroute vendors-round-product-detail "/v/rounds/:round-idstr/products/:product-idstr"
  [round-idstr product-idstr]
  (rf/dispatch [:v/route-round-product-detail round-idstr product-idstr]))

;; catch-all
(sec/defroute catch-all-path "*" [*]
  (if (= * "/a/search") ;; trying to get to /a/search but route doesn't exist?
    (.reload js/location) ;; special case to get the "full" js (needed for admin routes)
    (do (.log js/console "nav catch-all; path: " *)
        (rf/dispatch [:nav-home]))))

(rf/reg-event-fx
 :ws-get-session-user
 (fn []
   {:ws-send {:payload {:cmd :auth-by-session
                        :return :ws/req-session}}}))

(rf/reg-event-fx
 :ws/req-session
 [(rf/inject-cofx :local-store [:session-token])]
 (fn [{:keys [db local-store]} [_ {:keys [logged-in? user memberships
                                          admin-of-groups admin?]}]]
   (if logged-in?
     (let [org-id (some-> memberships first :org-id)]  ; TODO support users with multi-orgs]
       {:db (assoc db  
                   :logged-in? true
                   :user user
                   :memberships memberships
                   :active-memb-id (some-> memberships first :id)
                   :org-id org-id
                   :admin-of-groups admin-of-groups
                   ;; a Vetd employee with admin access?
                   :admin? admin?)
        :cookies {:admin-token (when admin? [(:session-token local-store) {:max-age 3600 :path "/"}])}
        :chrome-extension {:cmd "setVetdUser"
                           :args {:vetdUser user}}
        :after-req-session nil})
     {:after-req-session nil})))

(defn config-acct []
  (acct/configure-navigation!
   {:nav-handler (fn [requested-path]
                   (let [logged-in? (not (s/blank? (local-store/get-item :session-token)))
                         path-no-query-params (first (s/split requested-path #"\?"))
                         path (if (or logged-in?
                                      (public-routes (sec/locate-route-value path-no-query-params)))
                                path-no-query-params
                                "/login")]
                     (r/after-render clerk/after-render!)
                     (sec/dispatch! path)
                     (clerk/navigate-page! path)))
    :path-exists? sec/locate-route
    :reload-same-path? false}))

(defonce additional-init-done? (volatile! false))

;; additional init that must occur after :ws/req-session
(rf/reg-fx
 :after-req-session
 (fn []
   (when-not @additional-init-done?
     (vreset! additional-init-done? true)
     (clerk/initialize!)
     (config-acct)
     (acct/dispatch-current!)
     (mount-components))))

(defonce init-done? (volatile! false))

(defn init! []
  (if @init-done?
    (println "init! SKIPPED")
    (do
      (println "init! START")
      (vreset! init-done? true)
      (rf/dispatch-sync [:init-db])
      (rf/dispatch-sync [:reify-modes])
      (rf/dispatch-sync [:reg-sub-trackers])
      (rf/dispatch-sync [:ws-init])
      (rf/dispatch-sync [:ws-get-session-user])
      (println "init! END"))))

(println "END core")
