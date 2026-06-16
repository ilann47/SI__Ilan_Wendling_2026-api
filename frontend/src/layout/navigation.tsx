import { type ReactNode } from 'react';
import SpaceDashboardOutlinedIcon from '@mui/icons-material/SpaceDashboardOutlined';
import LocalParkingOutlinedIcon from '@mui/icons-material/LocalParkingOutlined';
import SwapHorizOutlinedIcon from '@mui/icons-material/SwapHorizOutlined';
import CardMembershipOutlinedIcon from '@mui/icons-material/CardMembershipOutlined';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import CategoryOutlinedIcon from '@mui/icons-material/CategoryOutlined';
import SellOutlinedIcon from '@mui/icons-material/SellOutlined';
import StraightenOutlinedIcon from '@mui/icons-material/StraightenOutlined';
import BuildOutlinedIcon from '@mui/icons-material/BuildOutlined';
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined';
import CallReceivedOutlinedIcon from '@mui/icons-material/CallReceivedOutlined';
import CallMadeOutlinedIcon from '@mui/icons-material/CallMadeOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import TrendingDownOutlinedIcon from '@mui/icons-material/TrendingDownOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import ReceiptOutlinedIcon from '@mui/icons-material/ReceiptOutlined';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import DirectionsCarOutlinedIcon from '@mui/icons-material/DirectionsCarOutlined';
import PriceChangeOutlinedIcon from '@mui/icons-material/PriceChangeOutlined';
import PaymentOutlinedIcon from '@mui/icons-material/PaymentOutlined';
import EventRepeatOutlinedIcon from '@mui/icons-material/EventRepeatOutlined';
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined';
import WorkOutlineIcon from '@mui/icons-material/WorkOutline';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import PublicOutlinedIcon from '@mui/icons-material/PublicOutlined';
import MapOutlinedIcon from '@mui/icons-material/MapOutlined';
import LocationCityOutlinedIcon from '@mui/icons-material/LocationCityOutlined';

export interface NavItem {
  label: string;
  path: string;
  icon: ReactNode;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const navGroups: NavGroup[] = [
  {
    label: 'Operação',
    items: [
      { label: 'Dashboard', path: '/app', icon: <SpaceDashboardOutlinedIcon fontSize="small" /> },
      { label: 'Pátio', path: '/app/patio', icon: <LocalParkingOutlinedIcon fontSize="small" /> },
      { label: 'Movimentações', path: '/app/movimentacoes', icon: <SwapHorizOutlinedIcon fontSize="small" /> },
      { label: 'Mensalistas', path: '/app/mensalistas', icon: <CardMembershipOutlinedIcon fontSize="small" /> },
      { label: 'Relatórios', path: '/app/relatorios', icon: <AssessmentOutlinedIcon fontSize="small" /> },
    ],
  },
  {
    label: 'Conveniência',
    items: [
      { label: 'Produtos', path: '/app/produtos', icon: <Inventory2OutlinedIcon fontSize="small" /> },
      { label: 'Categorias', path: '/app/categorias', icon: <CategoryOutlinedIcon fontSize="small" /> },
      { label: 'Marcas', path: '/app/marcas', icon: <SellOutlinedIcon fontSize="small" /> },
      { label: 'Unidades de Medida', path: '/app/unidades-medida', icon: <StraightenOutlinedIcon fontSize="small" /> },
      { label: 'Serviços', path: '/app/servicos', icon: <BuildOutlinedIcon fontSize="small" /> },
      { label: 'Produto x Fornecedor', path: '/app/produto-fornecedores', icon: <LinkOutlinedIcon fontSize="small" /> },
    ],
  },
  {
    label: 'Fiscal',
    items: [
      { label: 'Notas de Entrada', path: '/app/notas-entrada', icon: <CallReceivedOutlinedIcon fontSize="small" /> },
      { label: 'Notas de Saída', path: '/app/notas-saida', icon: <CallMadeOutlinedIcon fontSize="small" /> },
      { label: 'Notas de Serviço', path: '/app/notas-servico', icon: <DescriptionOutlinedIcon fontSize="small" /> },
    ],
  },
  {
    label: 'Financeiro',
    items: [
      { label: 'Contas a Pagar', path: '/app/contas-pagar', icon: <TrendingDownOutlinedIcon fontSize="small" /> },
      { label: 'Contas a Receber', path: '/app/contas-receber', icon: <TrendingUpOutlinedIcon fontSize="small" /> },
      { label: 'Despesas Avulsas', path: '/app/contas-pagar-avulsas', icon: <ReceiptOutlinedIcon fontSize="small" /> },
    ],
  },
  {
    label: 'Cadastros',
    items: [
      { label: 'Clientes', path: '/app/clientes', icon: <PeopleOutlinedIcon fontSize="small" /> },
      { label: 'Fornecedores', path: '/app/fornecedores', icon: <LocalShippingOutlinedIcon fontSize="small" /> },
      { label: 'Veículos', path: '/app/veiculos', icon: <DirectionsCarOutlinedIcon fontSize="small" /> },
      { label: 'Tarifas', path: '/app/tarifas', icon: <PriceChangeOutlinedIcon fontSize="small" /> },
      { label: 'Formas de Pagamento', path: '/app/formas-pagamento', icon: <PaymentOutlinedIcon fontSize="small" /> },
      { label: 'Condições de Pagamento', path: '/app/condicoes-pagamento', icon: <EventRepeatOutlinedIcon fontSize="small" /> },
    ],
  },
  {
    label: 'RH & Acesso',
    items: [
      { label: 'Cargos', path: '/app/cargos', icon: <BadgeOutlinedIcon fontSize="small" /> },
      { label: 'Funcionários', path: '/app/funcionarios', icon: <WorkOutlineIcon fontSize="small" /> },
      { label: 'Usuários', path: '/app/usuarios', icon: <ManageAccountsOutlinedIcon fontSize="small" /> },
    ],
  },
  {
    label: 'Geografia',
    items: [
      { label: 'Países', path: '/app/paises', icon: <PublicOutlinedIcon fontSize="small" /> },
      { label: 'Estados', path: '/app/estados', icon: <MapOutlinedIcon fontSize="small" /> },
      { label: 'Cidades', path: '/app/cidades', icon: <LocationCityOutlinedIcon fontSize="small" /> },
    ],
  },
];
