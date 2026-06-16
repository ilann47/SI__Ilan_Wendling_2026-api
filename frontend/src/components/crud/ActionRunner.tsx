import { useMemo } from 'react';
import { useMutation } from '@tanstack/react-query';
import { describeError } from '../../api/client';
import { createResourceApi } from '../../api/resource';
import { useSnackbar } from '../SnackbarProvider';
import { ConfirmDialog } from '../common/ConfirmDialog';
import { ResourceFormDialog } from '../form/ResourceFormDialog';
import { type RowAction } from './resourceConfig';

interface Props {
  basePath: string;
  action: RowAction;
  row: Record<string, any>;
  onClose: () => void;
  onDone: () => void;
}

export function ActionRunner({ basePath, action, row, onClose, onDone }: Props) {
  const resource = useMemo(() => createResourceApi(basePath), [basePath]);
  const { notify } = useSnackbar();

  const mutation = useMutation({
    mutationFn: (collected?: Record<string, unknown>) => {
      const options =
        action.payloadAs === 'params' ? { params: collected } : { body: collected };
      return resource.action(action.method, action.pathSuffix(row), options);
    },
    onSuccess: () => {
      notify(`${action.label} realizado com sucesso.`, 'success');
      onDone();
    },
    onError: (e) => notify(describeError(e), 'error'),
  });

  if (action.formFields && action.formFields.length > 0) {
    return (
      <ResourceFormDialog
        open
        title={action.label}
        fields={action.formFields}
        submitting={mutation.isPending}
        onClose={onClose}
        onSubmit={(values) => mutation.mutate(values)}
      />
    );
  }

  return (
    <ConfirmDialog
      open
      title={action.label}
      message={action.confirm ?? `Deseja realmente executar "${action.label}"?`}
      confirmLabel={action.label}
      confirmColor={action.color === 'error' ? 'error' : 'primary'}
      loading={mutation.isPending}
      onConfirm={() => mutation.mutate(undefined)}
      onClose={onClose}
    />
  );
}
