(ns vetd-app.buyers.pages.product-detail
  (:require [vetd-app.buyers.components :as bc]
            [vetd-app.common.components :as cc]
            [vetd-app.ui :as ui]
            [vetd-app.docs :as docs]
            [reagent.core :as r]
            [reagent.format :as format]
            [re-frame.core :as rf]))

;; Events
(rf/reg-event-fx
 :b/nav-product-detail
 (fn [_ [_ product-idstr]]
   {:nav {:path (str "/b/products/" product-idstr)}}))

(rf/reg-event-fx
 :b/route-product-detail
 (fn [{:keys [db]} [_ product-idstr]]
   {:db (assoc db
               :page :b/product-detail
               :page-params {:product-idstr product-idstr})
    :analytics/page {:name "Buyers Product Detail"
                     :props {:product-idstr product-idstr}}}))

;; Subscriptions
(rf/reg-sub
 :product-idstr
 :<- [:page-params] 
 (fn [{:keys [product-idstr]}] product-idstr))

;; Components
(defn c-product
  "Component to display Preposal details."
  [{:keys [id pname long-desc url logo vendor forms rounds categories] :as product}]
  [:div.detail-container
   [:> ui/Header {:size "huge"
                  :style {:margin "7px 7px 11px 7px"}}
    pname " " [:small " by " (:oname vendor)]]
   [:> ui/Image {:class "product-logo"
                 :src (str "https://s3.amazonaws.com/vetd-logos/" logo)}]
   (if (not-empty (:rounds product))
     [bc/c-round-in-progress {:props {:ribbon "left"}}])
   [bc/c-categories product]
   [:> ui/Grid {:columns "equal"
                :style {:margin-top 0}}
    [:> ui/GridRow
     [bc/c-display-field {:width 12} "Product Description"
      [:<> (or long-desc "No description available.")
       (when (not-empty url)
         [:p "Website: " [:a {:href (str "http://" url) ; todo: fragile
                              :target "_blank"}
                          [:> ui/Icon {:name "external square"
                                       :color "blue"}]
                          url]])]]]
    [:> ui/GridRow
     [bc/c-display-field {:width 6} "Pitch" "Unavailable (Request a Preposal)"]
     [bc/c-display-field {:width 6} "Estimated Price" "Unavailable (Request a Preposal)"]]
    (when (not= "" (:url vendor))
      [:> ui/GridRow
       [bc/c-display-field nil (str "About " (:oname vendor))
        [:span "Website: " [:a {:href (str "http://" (:url vendor)) ; todo: fragile
                                :target "_blank"}
                            [:> ui/Icon {:name "external square"
                                         :color "blue"}]
                            (:url vendor)]]]])]])

(defn c-page []
  (let [product-idstr& (rf/subscribe [:product-idstr])
        org-id& (rf/subscribe [:org-id])
        products& (rf/subscribe [:gql/sub
                                 {:queries
                                  [[:products {:idstr @product-idstr&}
                                    [:id :pname :logo :short-desc :long-desc :url
                                     [:vendor [:id :oname :url]]
                                     [:forms {:ftype "preposal" ; preposal requests
                                              :from-org-id @org-id&}
                                      [:id]]
                                     [:rounds {:buyer-id @org-id&
                                               :status "active"}
                                      [:id :created :status]]
                                     [:categories [:id :idstr :cname]]]]]}])]
    (fn []
      [:div.container-with-sidebar
       [:div.sidebar
        [:div {:style {:padding "0 15px"}}
         [:> ui/Button {:on-click #(rf/dispatch [:b/nav-search])
                        :color "gray"
                        :icon true
                        :size "small"
                        :style {:width "100%"}
                        :labelPosition "left"}
          "Back to Search"
          [:> ui/Icon {:name "left arrow"}]]]
        (when-not (= :loading @products&)
          (let [{:keys [vendor rounds forms] :as product} (-> @products& :products first)
                requested-preposal? (not-empty forms)]
            (when (empty? (:rounds product))
              [:> ui/Segment
               [bc/c-start-round-button {:etype :product
                                         :eid (:id product)
                                         :ename (:pname product)}]
               [:br]
               (if requested-preposal?
                 [:> ui/Popup
                  {:content "We will be in touch with next steps."
                   :header "Preposal Requested!"
                   :position "bottom left"
                   :trigger (r/as-element
                             [:> ui/Label {:color "teal"
                                           :size "large"
                                           :basic true}
                              "Preposal Requested"])}]
                 [:> ui/Popup
                  {:content (str "Get a pricing estimate, personalized pitch, and more from "
                                 (:oname vendor) ".")
                   :header "What is a Preposal?"
                   :position "bottom left"
                   :trigger (r/as-element
                             [:> ui/Button {:onClick #(rf/dispatch [:b/create-preposal-req product vendor])
                                            :color "teal"
                                            :fluid true
                                            :style {:marginRight 15}}
                              "Request Preposal"])}])
               
               ])))]
       [:> ui/Segment {:class "inner-container"}
        (if (= :loading @products&)
          [cc/c-loader]
          [c-product (-> @products& :products first)])]])))
