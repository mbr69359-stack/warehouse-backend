# Customer Return Draft Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe delete action for draft customer return records.

**Architecture:** The backend owns safety and consistency through a transactional service method that only deletes `DRAFT` customer return workflows. The frontend only exposes the operation for `DRAFT` rows and refreshes the list after success.

**Tech Stack:** Spring Boot 2.7, Java 8, MyBatis-Plus, Vue 2, Element UI.

---

## File Structure

- Modify: `D:\AI\warehouse-backend\src\main\java\com\warehouse\service\CustomerReturnService.java`
  Add `deleteDraft(Long returnId, Long operatorId)`.
- Modify: `D:\AI\warehouse-backend\src\main\java\com\warehouse\service\impl\CustomerReturnServiceImpl.java`
  Implement transactional draft-only deletion.
- Modify: `D:\AI\warehouse-backend\src\main\java\com\warehouse\controller\CustomerReturnController.java`
  Add `DELETE /customer-returns/{id}`.
- Create: `D:\AI\warehouse-backend\src\test\java\com\warehouse\service\CustomerReturnDeleteDraftTest.java`
  Unit tests for draft deletion and rejection.
- Modify: `D:\AI\warehouse-frontend\src\api\customerReturn.js`
  Add `deleteCustomerReturn`.
- Modify: `D:\AI\warehouse-frontend\src\views\customerReturn\Index.vue`
  Add the draft-only delete button and handler.

## Task 1: Backend Delete API

**Files:**
- Modify: `D:\AI\warehouse-backend\src\main\java\com\warehouse\service\CustomerReturnService.java`
- Modify: `D:\AI\warehouse-backend\src\main\java\com\warehouse\service\impl\CustomerReturnServiceImpl.java`
- Modify: `D:\AI\warehouse-backend\src\main\java\com\warehouse\controller\CustomerReturnController.java`
- Create: `D:\AI\warehouse-backend\src\test\java\com\warehouse\service\CustomerReturnDeleteDraftTest.java`

- [ ] **Step 1: Write failing tests**

Create `CustomerReturnDeleteDraftTest.java` with Mockito tests that construct `CustomerReturnServiceImpl` directly.

Required tests:

```java
@Test
void deleteDraft_removesReturnItemsLinkedDraftOrdersAndReturn() {
    // Arrange: return status DRAFT, inOrderId=10, outOrderId=20.
    // Mock linked InOrder and OutOrder as status DRAFT.
    // Act: service.deleteDraft(1L, 9L).
    // Assert:
    // - customerReturnItemMapper.delete(...) called.
    // - inOrderItemMapper.delete(...) called and inOrderMapper.deleteById(10L) called.
    // - outOrderItemMapper.delete(...) called and outOrderMapper.deleteById(20L) called.
    // - customerReturnMapper.deleteById(1L) called.
}

@Test
void deleteDraft_rejectsNonDraftReturnWithoutDeletingAnything() {
    // Arrange: return status INBOUND_DONE.
    // Act/Assert: BusinessException is thrown.
    // Assert no mapper delete methods are called.
}

@Test
void deleteDraft_rejectsWhenLinkedInboundOrderIsConfirmed() {
    // Arrange: return status DRAFT, linked InOrder status CONFIRMED.
    // Act/Assert: BusinessException is thrown.
    // Assert no delete methods are called.
}
```

Run:

```powershell
mvn -Dtest=CustomerReturnDeleteDraftTest test
```

Expected before implementation: compile failure because `deleteDraft` does not exist.

- [ ] **Step 2: Add service interface**

Add to `CustomerReturnService.java`:

```java
void deleteDraft(Long returnId, Long operatorId);
```

- [ ] **Step 3: Implement transactional delete**

Add to `CustomerReturnServiceImpl.java`:

```java
@Override
@Transactional
public void deleteDraft(Long returnId, Long operatorId) {
    CustomerReturn ret = customerReturnMapper.selectByIdForUpdate(returnId);
    if (ret == null) throw new BusinessException("退换货单不存在");
    if (!"DRAFT".equals(ret.getStatus())) throw new BusinessException("只有草稿退换货单可以删除");

    if (ret.getInOrderId() != null) {
        InOrder inOrder = inOrderMapper.selectById(ret.getInOrderId());
        if (inOrder != null && !"DRAFT".equals(inOrder.getStatus())) {
            throw new BusinessException("退货入库单已流转，不能删除草稿");
        }
    }

    if (ret.getOutOrderId() != null) {
        OutOrder outOrder = outOrderMapper.selectById(ret.getOutOrderId());
        if (outOrder != null && !"DRAFT".equals(outOrder.getStatus())) {
            throw new BusinessException("补发出库单已流转，不能删除草稿");
        }
    }

    customerReturnItemMapper.delete(new LambdaQueryWrapper<CustomerReturnItem>()
            .eq(CustomerReturnItem::getReturnId, returnId));

    if (ret.getInOrderId() != null) {
        inOrderItemMapper.delete(new LambdaQueryWrapper<InOrderItem>()
                .eq(InOrderItem::getOrderId, ret.getInOrderId()));
        inOrderMapper.deleteById(ret.getInOrderId());
    }

    if (ret.getOutOrderId() != null) {
        outOrderItemMapper.delete(new LambdaQueryWrapper<OutOrderItem>()
                .eq(OutOrderItem::getOrderId, ret.getOutOrderId()));
        outOrderMapper.deleteById(ret.getOutOrderId());
    }

    customerReturnMapper.deleteById(returnId);
}
```

- [ ] **Step 4: Add controller endpoint**

Add to `CustomerReturnController.java`:

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public Result<Void> delete(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails user) {
    customerReturnService.deleteDraft(id, ((JwtUserDetails) user).getUserId());
    return Result.success();
}
```

- [ ] **Step 5: Verify backend**

Run:

```powershell
mvn -Dtest=CustomerReturnDeleteDraftTest test
```

Expected: tests pass.

## Task 2: Frontend Delete Button

**Files:**
- Modify: `D:\AI\warehouse-frontend\src\api\customerReturn.js`
- Modify: `D:\AI\warehouse-frontend\src\views\customerReturn\Index.vue`

- [ ] **Step 1: Add API function**

Add to `customerReturn.js`:

```javascript
export const deleteCustomerReturn = id => request.delete(`/customer-returns/${id}`)
```

- [ ] **Step 2: Import API in page**

Update the import list in `Index.vue` to include:

```javascript
deleteCustomerReturn
```

- [ ] **Step 3: Add draft-only delete button**

In the operation column, add after the detail button:

```vue
<el-button v-if="row.status === 'DRAFT'" size="mini" type="danger" plain
  @click="handleDelete(row)">删除草稿</el-button>
```

- [ ] **Step 4: Add handler**

Add a method:

```javascript
async handleDelete(row) {
  await this.$confirm(`确认删除退换货草稿 ${row.exchangeNo}？关联的退货入库草稿和补发出库草稿也会一起删除。`, '删除确认', {
    type: 'warning'
  })
  await deleteCustomerReturn(row.id)
  this.$message.success('草稿已删除')
  this.loadData()
}
```

- [ ] **Step 5: Verify frontend**

Run:

```powershell
npm run build
```

Expected: build exits 0.

## Final Verification

- Run backend targeted test.
- Run frontend build.
- Inspect `git diff` for both repositories.
- Confirm no database files, SQL migrations, deployment files, or unrelated dirty docs were modified.
- Do not push or deploy until the user approves deployment.
