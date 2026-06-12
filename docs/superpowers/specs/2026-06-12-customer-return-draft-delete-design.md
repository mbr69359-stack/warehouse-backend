# Customer Return Draft Delete Design

## Goal

Allow users to delete draft records from the customer return management page, so abandoned draft return/exchange documents do not stay in the list forever.

## Scope

This change applies to the production warehouse project:

- Backend: `D:\AI\warehouse-backend`
- Frontend: `D:\AI\warehouse-frontend`

Do not modify `D:\ModernWMS`. Do not connect to or mutate any database directly. Do not add or run SQL migration files for this change.

## Behavior

Only `customer_return` records with status `DRAFT` can be deleted from the customer return management UI.

Deleting a draft customer return must be a single backend transaction that removes:

- The `customer_return_item` rows for the return.
- The linked return inbound draft order and its `in_order_item` rows, when the linked inbound order still exists and is `DRAFT`.
- The linked replacement outbound draft order and its `out_order_item` rows, when the linked outbound order still exists and is `DRAFT`.
- The `customer_return` row itself.

The backend must reject deletion when the customer return status is not `DRAFT`. It must also reject deletion if either linked order exists but has already moved beyond `DRAFT`, because that means the return workflow has affected business state.

## API

Add:

```http
DELETE /customer-returns/{id}
```

Authorization should match other staff/admin operational customer return actions:

```java
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
```

Successful deletion returns the standard empty success result.

## Frontend

In `src/views/customerReturn/Index.vue`, show a danger-style delete button only for `DRAFT` rows. The button must ask for confirmation before calling the API.

After successful deletion, show a success message and refresh the list.

## Safety

This is a code-only change. It must not:

- Read or write production database data directly.
- Modify migration SQL.
- Modify deployment configuration.
- Push or deploy without user approval.

## Verification

Backend:

- Add unit tests around `CustomerReturnServiceImpl.deleteDraft`.
- Verify draft deletion removes the return, its return items, and draft linked orders/items.
- Verify non-draft deletion is rejected and performs no deletes.
- Run the targeted Maven test.

Frontend:

- Run production build to catch template/import errors.
