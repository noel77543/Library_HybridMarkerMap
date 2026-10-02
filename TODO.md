# 待辦事項

## 產品補強

- [x] **資料模型改為泛型（最優先）**（2026-10-02 完成：`HybridMarkerMap<C, S>`、`ClusterableMarker<T>` / `StandaloneMarker<T>` 帶 payload）
  `ClusterableMarker` / `StandaloneMarker` 只有 `id / name / lat / lng / statusValue`，使用者無法掛上自己的資料物件，點擊後還得拿 id 回頭查。
  應改成泛型，例如 `ClusterableMarker<T>` 帶一個 `payload`。會變動 API，越早做影響越小。

- [ ] **Compose 支援**
  新專案大多直接用 Compose，Google 也有官方的 maps-compose。
  至少提供 Compose 包裝，或在文件說明如何搭配使用。

- [ ] **發布到 Maven Central**
  JitPack 信任度較低，許多公司專案只接受 Maven Central 上的套件。
  2026-10-02 建置設定完成：座標 `io.github.noel77543:hybridmarkermap:0.1.0`、Apache 2.0、POM／sources／javadoc 齊全，本機發布已驗證。
  剩下需要本人操作的步驟（見 README「發布」）：
  - [ ] 建立 GitHub repo `noel77543/Library_HybridMarkerMap` 並推上程式碼（POM 的 url／scm 指向此處）
  - [ ] 以 GitHub 帳號登入 Central Portal、產生 User Token
  - [ ] 建立 GPG 金鑰並上傳公鑰到 keyserver
  - [ ] 設定 `~/.gradle/gradle.properties` 後執行 `publishToMavenCentral`，到 Central Portal 按 Publish

- [ ] **自動化測試與 demo GIF**
  補上單元測試與 CI；README 第一屏放 GIF。使用者打開 README 的前 5 秒就決定要不要繼續看。

- [x] **確認地圖樣式的長期方案**（2026-10-02 確認，維持 JSON 樣式）
  查證結果：JSON 樣式（`MapStyleOptions` / `setMapStyle`）在官方文件中仍完整支援，沒有棄用標示；
  被棄用的是「舊版雲端樣式」（legacy cloud styling，2025-03 已自動遷移至新版），與 JSON 樣式無關。
  Google 較推薦雲端樣式（Map ID），但需要後台設定，不適合「裝了就能用」的 lib。
  官方建議不要與雲端樣式混用，已在 README 加上說明。

## 待驗證

- [ ] 圓點錨點修正（`setStandaloneIconAnchor`）尚未在實機上確認；手機重新連線後安裝並截圖檢查
