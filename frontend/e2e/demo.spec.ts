import { expect, test } from "@playwright/test";

test("un visitante inicia una sesión demo y ve sus fondos simulados", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("button", { name: /Probar FinPay Demo/ }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByText("Demo · fondos simulados")).toBeVisible();
  await expect(page.getByText("Saldo en EUR")).toBeVisible();
  await expect(page.getByText(/10\.000,00/)).toBeVisible();
});

test("la sesión demo se elimina al cerrar sesión", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("button", { name: /Probar FinPay Demo/ }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.getByRole("button", { name: "Cerrar sesión" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("button", { name: /Probar FinPay Demo/ })).toBeVisible();
});
