package com.mawten.colony;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import androidx.browser.trusted.Token;
import com.google.androidbrowserhelper.trusted.SharedPreferencesTokenStore;
import com.android.billingclient.api.*;
import org.json.JSONObject;
import java.util.Collections;
import java.util.List;

/** Verified TWA-only checkout with server-bound account and checkout identifiers. */
public final class MawtenPaymentActivity extends Activity implements PurchasesUpdatedListener {
 private static final String METHOD="https://play.google.com/billing";
 private static final String HOST="mawten-wadi-colony.moazkhoulani.workers.dev";
 private BillingClient billing;private String sku,account,checkout;private boolean finished;private boolean subscription(){return sku.equals("noads_monthly")||sku.equals("premium_monthly");}
 private boolean allowedOrigin(String origin){return origin!=null&&(origin.equals(HOST)||origin.equals("https://"+HOST)||origin.equals("https://"+HOST+"/"));}
 // TokenStore inherits a BinderThread annotation; this implementation only reads thread-safe SharedPreferences.
 @SuppressLint("WrongThread")
 @Override public void onCreate(Bundle state){super.onCreate(state);try{
  ComponentName caller=getCallingActivity();Token trusted=new SharedPreferencesTokenStore(this).load();
  if(caller==null||trusted==null||!trusted.matches(caller.getPackageName(),getPackageManager())||!allowedOrigin(getIntent().getStringExtra("topLevelOrigin"))||!allowedOrigin(getIntent().getStringExtra("paymentRequestOrigin"))){fail("Unverified checkout origin");return;}
  Bundle methods=getIntent().getBundleExtra("methodData");JSONObject data=new JSONObject(methods==null?"{}":methods.getString(METHOD,"{}"));
  sku=data.optString("sku");account=data.optString("obfuscatedAccountId");checkout=data.optString("obfuscatedProfileId");
  if(!(sku.equals("gold200")||sku.equals("gold500")||sku.equals("gold1000")||subscription())||!account.matches("[a-f0-9]{64}")||!checkout.matches("[a-f0-9]{32}")){fail("Invalid checkout");return;}
  billing=BillingClient.newBuilder(this).setListener(this).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build()).enableAutoServiceReconnection().build();
  billing.startConnection(new BillingClientStateListener(){public void onBillingSetupFinished(BillingResult result){if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){fail("Google Play unavailable");return;}query();}public void onBillingServiceDisconnected(){}});
 }catch(Exception e){fail("Checkout initialization failed");}}
 private void query(){QueryProductDetailsParams params=QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder().setProductId(sku).setProductType(subscription()?BillingClient.ProductType.SUBS:BillingClient.ProductType.INAPP).build())).build();
 billing.queryProductDetailsAsync(params,(result,details)->runOnUiThread(()->{try{
  if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK||details.getProductDetailsList().isEmpty()){fail("Product unavailable");return;}
  ProductDetails product=details.getProductDetailsList().get(0);BillingFlowParams.ProductDetailsParams.Builder item=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product);
  if(subscription()){List<ProductDetails.SubscriptionOfferDetails> plans=product.getSubscriptionOfferDetails();ProductDetails.SubscriptionOfferDetails selected=null;if(plans!=null)for(ProductDetails.SubscriptionOfferDetails plan:plans)if(plan.getOfferId()==null&&plan.getBasePlanId().equals(sku.equals("noads_monthly")?"monthly-prepaid":"monthly")){selected=plan;break;}if(selected==null){fail("Monthly subscription unavailable");return;}item.setOfferToken(selected.getOfferToken());}
  else {List<ProductDetails.OneTimePurchaseOfferDetails> offers=product.getOneTimePurchaseOfferDetailsList();if(offers!=null&&!offers.isEmpty()&&offers.get(0).getOfferToken()!=null)item.setOfferToken(offers.get(0).getOfferToken());}
  BillingFlowParams flow=BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(item.build())).setObfuscatedAccountId(account).setObfuscatedProfileId(checkout).build();
  if(billing.launchBillingFlow(this,flow).getResponseCode()!=BillingClient.BillingResponseCode.OK)fail("Could not launch Google Play checkout");
 }catch(Exception e){fail("Product setup failed");}}));}
 @Override public void onPurchasesUpdated(BillingResult result,List<Purchase> purchases){if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK||purchases==null){fail(result.getResponseCode()==BillingClient.BillingResponseCode.USER_CANCELED?"Purchase cancelled":"Purchase not completed");return;}for(Purchase p:purchases)if(p.getProducts().contains(sku)){try{JSONObject details=new JSONObject();details.put("purchaseToken",p.getPurchaseToken());details.put("pending",p.getPurchaseState()==Purchase.PurchaseState.PENDING);complete(RESULT_OK,details);}catch(Exception e){fail("Purchase response failed");}return;}fail("No matching purchase");}
 private void fail(String error){try{JSONObject details=new JSONObject();details.put("error",error);complete(RESULT_CANCELED,details);}catch(Exception ignored){finish();}}
 private void complete(int result,JSONObject details){if(finished)return;finished=true;Intent response=new Intent();response.putExtra("methodName",METHOD);response.putExtra("details",details.toString());setResult(result,response);finish();}
 @Override protected void onDestroy(){if(billing!=null)billing.endConnection();super.onDestroy();}
}
